/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import java.io.PrintStream;

/**
 * Date: 08.12.13
 *
 * @author Tor C Bekkvik
 */
class Matrix implements IMatrix {
    final int nRows, nCols;
    protected final double[][] data;

    public Matrix(int nRow, int nCol) {
        nRows = nRow;
        nCols = nCol;
        data = new double[nRows][nCols];
    }

    @Override
    public int noRows() {return nRows;}

    @Override
    public int noCols() {return nCols;}

    @Override
    public double value(int row, int col) {return data[row][col];}

    public void set(int row, int col, double val) {
        data[row][col] = val;
    }

    @Override
    public String toString() {
        return String.format("Matrix,  rows: %d   cols: %d", nRows, nCols);
    }

    public void toString(PrintStream ps) {
        ps.print("Matrix:");
        for (int i = 0; i < nRows; i++) {
            ps.print(String.format("\n  %5d :   ", i));
            for (int j = 0; j < nCols; j++)
                ps.print(String.format(" %6.0f", data[i][j]));
        }
    }
}
