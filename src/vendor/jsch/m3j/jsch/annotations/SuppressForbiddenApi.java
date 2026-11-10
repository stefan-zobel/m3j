// From JSch, distributed under the license in licenses/BSD-JSCH.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.jsch.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD,
    ElementType.PARAMETER, ElementType.TYPE})
public @interface SuppressForbiddenApi {
  String[] value();
}
