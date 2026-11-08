<*EXTERNAL "java.util.Collections":JAVA*> INTERFACE B;

TYPE Compare = PROCEDURE (a: REFANY): INTEGER;

<*EXTERNAL "sort(Ljava/util/List;Ljava/util/Comparator;)V"*>
PROCEDURE sort (list: REFANY; c: Compare);

END B.
