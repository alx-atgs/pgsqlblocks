/*
 * Copyright 2017 "Technology" LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.taximaxim.pgsqlblocks.process;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import ru.taximaxim.pgsqlblocks.utils.Settings;

public class ProcessTreeContentProvider implements ITreeContentProvider {

    private final Settings settings;

    public ProcessTreeContentProvider(Settings settings) {
        this.settings = settings;
    }

    /**
     * Gets the children of the specified object
     * 
     * @param arg0
     *            the parent object
     * @return Object[]
     */
    public Object[] getChildren(Object arg0) {
        Process process = (Process) arg0;
        if (settings.isOnlyBlocked()) {
            return process.getChildren().stream()
                    .filter(proc -> proc.getStatus() == ProcessStatus.BLOCKED || proc.getStatus() == ProcessStatus.BLOCKING)
                    .toArray();
        } else {
            return process.getChildren().toArray();
        }
    }

    /**
     * Gets the parent of the specified object
     * 
     * @param arg0
     *            the object
     * @return Object
     */
    public Object getParent(Object arg0) {
        return null;
    }

    /**
     * Returns whether the passed object has children
     * 
     * @param arg0
     *            the parent object
     * @return boolean
     */
    public boolean hasChildren(Object arg0) {
        return ((Process) arg0).hasChildren();
    }

    /**
     * Gets the root element(s) of the tree
     * 
     * @param arg0
     *            the input data
     * @return Object[]
     */
    public Object[] getElements(Object arg0) {
        return getChildren(arg0);
    }

    /**
     * Disposes any created resources
     */
    public void dispose() {
        // TODO Auto-generated method stub
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        // TODO Auto-generated method stub
    }
}