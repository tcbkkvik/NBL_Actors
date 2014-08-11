/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Date: 15.08.13
 *
 * @author Tor C Bekkvik
 */
public class MatrixMain {

    static IMatrix makeMatrix(int ni, int nj) {
        Random random = new Random();
        Matrix mat = new Matrix(ni, nj);
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                mat.data[i][j] = random.nextInt(18) - 8;
            }
        }
        return mat;
    }

    static void log(Object o) {
        if (o instanceof Matrix) {
            Matrix mat = (Matrix) o;
            mat.toString(System.out);
            System.out.println();
            return;
        }
        System.out.println(o);
    }

    static double sum(IMatrix mat) {
        double sum = 0;
        for (int r = 0; r < mat.noRows(); r++) {
            for (int c = 0; c < mat.noCols(); c++) {
                sum += mat.value(r, c);
            }
        }
        return sum;
    }

    static boolean isEqual(IMatrix a, IMatrix b, double err) {
        if (a.noCols() != b.noCols())
            return false;
        if (a.noRows() != b.noRows())
            return false;
        for (int i = 0; i < a.noRows(); i++) {
            for (int j = 0; j < a.noCols(); j++) {
                double aVal = a.value(i, j);
                double bVal = b.value(i, j);
                if (Math.abs(aVal - bVal) > err)
                    return false;
            }
        }
        return true;
    }

    static double vectorProduct(IMatrix a, IMatrix b, int i, int k) {
        final int J = a.noCols();
        if (b.noRows() != J) throw new IllegalArgumentException("matrix size mismatch");
        double sum = 0;
        for (int j = 0; j < J; j++)
            sum += a.value(i, j) * b.value(j, k);
        return sum;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static IASync<IMatrix> calculate(final IMatrix A, final IMatrix B, IGreenThrFactory gf) {
        final Matrix matrix = new Matrix(A.noRows(), B.noCols());
        final AtomicInteger rest = new AtomicInteger(matrix.noRows() * matrix.noCols());
        ForkJoin<IMatrix> fj = new ForkJoin<>((IMatrix) matrix);
        for (int r = 0; r < matrix.noRows(); r++) {
            final int row = r;
            for (int c = 0; c < matrix.noCols(); c++) {
                final int col = c;
                //green threads forked here..
                fj.call(gf.newThread(), () ->
                        vectorProduct(A, B, row, col),
                        prod -> {
                            matrix.set(row, col, prod);
                            rest.decrementAndGet();
                        });
            }
        }
        return fj.resultAsync();
    }


    static IMatrix matrixProduct(IMatrix A, IMatrix B) throws Exception {
        try (IGreenThrFactory factory = new GreenThrFactory_single(4)) {
            CompletableFuture<IMatrix> fut = new CompletableFuture<>();
            factory.newThread().execute(
                    () -> calculate(A, B, factory).result(fut::complete)
            );
            return fut.get();
        }
    }

    public static void main(String[] args) throws Exception {
        int k = 1; //max 1500? (-Xmx2000m)
        @SuppressWarnings("UnnecessaryLocalVariable")
        final int II = k, JJ = 2 * k, KK = II + 6;
        IMatrix aa = makeMatrix(II, JJ);
        IMatrix bb = makeMatrix(JJ, KK);
        if (k < 101) {
            log(aa);
            log(bb);
        }
        IMatrix prev = null;
        final int N = 2;
        for (int i = 0; i < N; i++) {
            long t0 = System.currentTimeMillis();
            IMatrix result = matrixProduct(aa, bb);
            long dt = System.currentTimeMillis() - t0;
            double sec = (double) dt / 1000.;
            if (k < 101) {
                log("Result ");
                log(result);
            }
            boolean ok = true;
            if (prev != null) {
                ok = isEqual(prev, result, 1e-5);
            }
            prev = result;
            log(String.format("ijk:  %d  %d  %d     #Green threads: %d,    #products: %d" +
                    "\n  dSeconds  : %6.3f  ok:%s" +
                    "\n    sum(aa) : %9.1f" +
                    "\n    sum(bb) : %9.1f" +
                    "\n    sum(res): %9.1f"
                    , II, JJ, KK
                    , II * KK, (long) II * JJ * KK
                    , sec, ok
                    , sum(aa), sum(bb), sum(prev)
            ));
        }

    }

}
