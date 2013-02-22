package javac.util;
/* Continuation for tail recursive call
 * This file is to be deprecated due to extra cost introduced.
 * This is just a demo of how continuation can be used in Java,
 * And why Tiger should consider choosing functional language.
 */

public interface Cont {
	boolean isNull();
}
