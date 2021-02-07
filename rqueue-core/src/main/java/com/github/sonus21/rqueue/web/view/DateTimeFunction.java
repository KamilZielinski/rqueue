/*
 *  Copyright 2021 Sonu Kumar
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *
 */

package com.github.sonus21.rqueue.web.view;

import com.github.sonus21.rqueue.utils.DateTimeUtils;
import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.SimpleJtwigFunction;

public class DateTimeFunction extends SimpleJtwigFunction {

  @Override
  public String name() {
    return "time";
  }

  @Override
  public Object execute(FunctionRequest request) {
    Long milli = (Long) request.getArguments().get(0);
    return DateTimeUtils.formatMilliToString(milli);
  }
}
