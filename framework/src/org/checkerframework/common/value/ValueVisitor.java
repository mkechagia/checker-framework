package org.checkerframework.common.value;
/*>>>
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
*/
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.common.value.qual.ArrayLen;
import org.checkerframework.common.value.qual.ArrayLenRange;
import org.checkerframework.common.value.qual.BoolVal;
import org.checkerframework.common.value.qual.DoubleVal;
import org.checkerframework.common.value.qual.IntRange;
import org.checkerframework.common.value.qual.IntRangeFromPositive;
import org.checkerframework.common.value.qual.IntVal;
import org.checkerframework.common.value.qual.StringVal;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.visitor.SimpleAnnotatedTypeScanner;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.InternalUtils;

/**
 * @author plvines
 *     <p>Visitor for the Constant Value type-system
 */
public class ValueVisitor extends BaseTypeVisitor<ValueAnnotatedTypeFactory> {

    public ValueVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    /**
     * @param exp an integral literal tree
     * @return {@code exp}'s literal value
     */
    private long getIntLiteralValue(LiteralTree exp) {
        Object orgValue = exp.getValue();
        switch (exp.getKind()) {
            case INT_LITERAL:
            case LONG_LITERAL:
                return ((Number) orgValue).longValue();
            case CHAR_LITERAL:
                return (long) ((Character) orgValue);
            default:
                throw new IllegalArgumentException(
                        "exp is not an intergral literal (INT_LITERAL, LONG_LITERAL, CHAR_LITERAL)");
        }
    }

    /**
     * ValueVisitor overrides this method so that it does not have to check variables annotated with
     * the {@link IntRangeFromPositive} annotation. This annotation is only introduced by the Index
     * Checker's {@link org.checkerframework.checker.index.qual.Positive} annotation. It is safe to
     * defer checking of these values to the Index Checker because this is only introduced for
     * explicitly-written {@link org.checkerframework.checker.index.qual.Positive} annotations,
     * which must be checked by the Lower Bound Checker.
     *
     * @param varType the annotated type of the lvalue (usually a variable)
     * @param valueExp the AST node for the rvalue (the new value)
     * @param errorKey the error message to use if the check fails (must be a compiler message key,
     */
    @Override
    protected void commonAssignmentCheck(
            AnnotatedTypeMirror varType,
            ExpressionTree valueExp,
            /*@CompilerMessageKey*/ String errorKey) {

        SimpleAnnotatedTypeScanner<Void, Void> replaceIntRangeFromPositive =
                new SimpleAnnotatedTypeScanner<Void, Void>() {
                    @Override
                    protected Void defaultAction(AnnotatedTypeMirror type, Void p) {
                        if (type.hasAnnotation(IntRangeFromPositive.class)) {
                            type.replaceAnnotation(atypeFactory.UNKNOWNVAL);
                        }
                        return null;
                    }
                };

        replaceIntRangeFromPositive.visit(varType);
        super.commonAssignmentCheck(varType, valueExp, errorKey);
    }

    @Override
    protected ValueAnnotatedTypeFactory createTypeFactory() {
        return new ValueAnnotatedTypeFactory(checker);
    }

    /**
     * Warns about malformed constant-value annotations.
     *
     * <p>Issues an error if any @IntRange annotation has its 'from' value greater than 'to' value.
     *
     * <p>Issues a warning if any constant-value annotation has &gt; MAX_VALUES arguments.
     */
    @Override
    public Void visitAnnotation(AnnotationTree node, Void p) {
        List<? extends ExpressionTree> args = node.getArguments();

        if (args.isEmpty()) {
            // Nothing to do if there are no annotation arguments.
            return super.visitAnnotation(node, p);
        }

        AnnotationMirror anno = InternalUtils.annotationFromAnnotationTree(node);

        if (AnnotationUtils.areSameByClass(anno, IntRange.class)) {
            // If there are 2 arguments, issue an error if from.greater.than.to.
            // If there are fewer than 2 arguments, we needn't worry about this problem because the
            // other argument will be defaulted to Long.MIN_VALUE or Long.MAX_VALUE accordingly.
            if (args.size() == 2) {
                long from = AnnotationUtils.getElementValue(anno, "from", Long.class, true);
                long to = AnnotationUtils.getElementValue(anno, "to", Long.class, true);
                if (from > to) {
                    checker.report(Result.failure("from.greater.than.to"), node);
                    return null;
                }
            }
        } else if (AnnotationUtils.areSameByClass(anno, ArrayLen.class)
                || AnnotationUtils.areSameByClass(anno, BoolVal.class)
                || AnnotationUtils.areSameByClass(anno, DoubleVal.class)
                || AnnotationUtils.areSameByClass(anno, IntVal.class)
                || AnnotationUtils.areSameByClass(anno, StringVal.class)) {
            List<Object> values =
                    AnnotationUtils.getElementValueArray(anno, "value", Object.class, true);

            if (values.isEmpty()) {
                checker.report(Result.warning("no.values.given"), node);
                return null;
            } else if (values.size() > ValueAnnotatedTypeFactory.MAX_VALUES) {
                checker.report(
                        Result.warning(
                                (AnnotationUtils.areSameByClass(anno, IntVal.class)
                                        ? "too.many.values.given.int"
                                        : "too.many.values.given"),
                                ValueAnnotatedTypeFactory.MAX_VALUES),
                        node);
                return null;
            } else if (AnnotationUtils.areSameByClass(anno, ArrayLen.class)) {
                List<Integer> arrayLens =
                        AnnotationUtils.getElementValueArray(anno, "value", Integer.class, true);
                if (Collections.min(arrayLens) < 0) {
                    checker.report(
                            Result.warning("negative.arraylen", Collections.min(arrayLens)), node);
                    return null;
                }
            }
        } else if (AnnotationUtils.areSameByClass(anno, ArrayLenRange.class)) {
            int from = AnnotationUtils.getElementValue(anno, "from", Integer.class, true);
            int to = AnnotationUtils.getElementValue(anno, "to", Integer.class, true);
            if (from > to) {
                checker.report(Result.failure("from.greater.than.to"), node);
                return null;
            } else if (from < 0) {
                checker.report(Result.warning("negative.arraylen", from), node);
                return null;
            }
        }

        return super.visitAnnotation(node, p);
    }

    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        if (node.getExpression().getKind() == Kind.NULL_LITERAL) {
            return null;
        }
        return super.visitTypeCast(node, p);
    }
}
