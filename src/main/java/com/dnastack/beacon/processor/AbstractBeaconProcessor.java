/*
 * The MIT License
 *
 * Copyright 2014 Miroslav Cupak (mirocupak@gmail.com).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.dnastack.beacon.processor;

import com.dnastack.beacon.dto.BeaconTo;
import com.dnastack.beacon.dto.QueryTo;
import com.dnastack.beacon.entity.Reference;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;

/**
 * Abstract beacon service handling multiple genome specific queries.
 *
 * @author Miroslav Cupak (mirocupak@gmail.com)
 * @version 1.0
 */
public abstract class AbstractBeaconProcessor implements BeaconProcessor, Serializable {

    private static final long serialVersionUID = 10L;

    private List<Future<String>> executeQueriesInParallel(BeaconTo beacon, QueryTo query) {
        List<Future<String>> fs = new ArrayList<>();
        if (query.getReference() == null) {
            // query all refs
            for (Reference ref : getSupportedReferences()) {
                fs.add(getQueryResponse(beacon, new QueryTo(query.getChromosome(), query.getPosition(), query.getAllele(), ref)));
            }
        } else if (getSupportedReferences().contains(query.getReference())) {
            // query only the specified ref
            fs.add(getQueryResponse(beacon, query));
        }

        return fs;
    }

    private List<Future<Boolean>> parseResultsInParallel(List<Future<String>> fs) {
        List<Future<Boolean>> bs = new ArrayList<>();
        for (Future<String> f : fs) {
            try {
                bs.add(parseQueryResponse(f.get()));
            } catch (InterruptedException | ExecutionException ex) {
                // ignore
            }
        }

        return bs;
    }

    private Boolean collectResults(List<Future<Boolean>> bs) {
        Boolean res = null;

        for (Future<Boolean> b : bs) {
            Boolean r = null;
            try {
                r = b.get();
            } catch (InterruptedException | ExecutionException ex) {
                // ignore, already null
            }
            if (r != null) {
                if (r) {
                    res = r;
                    break;
                } else {
                    if (res == null) {
                        res = r;
                    }
                }
            }
        }

        return res;
    }

    @Override
    @Asynchronous
    public Future<Boolean> executeQuery(BeaconTo beacon, QueryTo query) {
        Boolean res = null;

        if (query != null) {
            res = collectResults(parseResultsInParallel(executeQueriesInParallel(beacon, query)));
        }

        return new AsyncResult<>(res);
    }
}
