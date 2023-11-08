
// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file is autogenerated by
//     java_cpp_enum.py
// From
//     ../../../api/priority.h

package org.webrtc;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
        Priority.VERY_LOW, Priority.LOW, Priority.MEDIUM, Priority.HIGH
})
@Retention(RetentionPolicy.SOURCE)
public @interface Priority {
    int VERY_LOW = 0;
    int LOW = 1;
    int MEDIUM = 2;
    int HIGH = 3;
}
