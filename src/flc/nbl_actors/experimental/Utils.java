/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import java.util.*;

/**
 * Date: 01.09.13
 *
 * @author Tor C Bekkvik
 */
public class Utils {

    public static List<Integer> randomIntegers(int sz) {
        List<Integer> lst = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < sz; i++) {
            lst.add(rand.nextInt(sz * 10));
        }
        return lst;
    }

    /**
     * Split list in two.
     *
     * @param lst original list. (Unmodifiable from returned list)
     * @param <T> type
     * @return list of two lists
     */
    public static <T> List<List<T>> split(List<T> lst) {
        List<List<T>> tmp = new ArrayList<>();
        int N = lst.size();
        tmp.add(new ArrayList<>(lst.subList(0, N / 2)));
        tmp.add(new ArrayList<>(lst.subList(N / 2, N)));
        return tmp;
    }

    public static <T> List<T> sort(List<T> unsorted, Comparator<T> comp) {
        List<T> lst = new ArrayList<>(unsorted);
        lst.sort(comp);
        return lst;
    }

    //for demo only (inefficient)
    public static <T> void merge(List<T> a, List<T> b, Comparator<T> comp) {
        b.addAll(a);
        b.sort(comp);
    }

}
