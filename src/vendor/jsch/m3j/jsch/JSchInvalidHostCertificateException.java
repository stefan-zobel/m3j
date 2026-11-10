// From JSch, distributed under the license in licenses/BSD-JSCH.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.jsch;

public class JSchInvalidHostCertificateException extends JSchHostKeyException {

  private static final long serialVersionUID = -1L;

  JSchInvalidHostCertificateException(String s) {
    super(s);
  }
}
