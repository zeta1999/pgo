package pgo.trans.passes.codegen.go;

import pgo.model.golang.*;
import pgo.model.golang.builder.GoBlockBuilder;
import pgo.model.tla.TLAExpression;
import pgo.model.type.Type;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeGenUtil {
	private CodeGenUtil() {}

	public static GoExpression invertCondition(GoBlockBuilder builder, DefinitionRegistry registry,
											   Map<UID, Type> typeMap,
											   LocalVariableStrategy localStrategy,
											   GlobalVariableStrategy globalStrategy,
											   TLAExpression condition) {
		return new GoUnary(GoUnary.Operation.NOT, condition.accept(new TLAExpressionCodeGenVisitor(builder, registry, typeMap, localStrategy, globalStrategy)));
	}

	public static GoExpression staticallySortSlice(GoSliceLiteral slice){
		return new GoSliceLiteral(
				slice.getElementType(),
				slice.getInitializers().stream()
						.sorted((lhs, rhs) -> lhs.accept(
								new GoExpressionStaticComparisonVisitor(rhs)))
						.distinct()
						.collect(Collectors.toList()));
	}

	static void generateArgumentParsing(GoBlockBuilder builder, GoExpression expression, GoVariableName processName,
										GoVariableName processArgument) {
		builder.addImport("pgo/distsys");
		builder.assign(
				Arrays.asList(processName, processArgument),
				new GoCall(
						new GoSelectorExpression(new GoVariableName("distsys"), "ParseProcessId"),
						Collections.singletonList(expression)));
	}
}
