package pgo.trans.passes.codegen.pluscal;

import pgo.Unreachable;
import pgo.model.mpcal.ModularPlusCalMapping;
import pgo.model.mpcal.ModularPlusCalYield;
import pgo.model.pcal.*;
import pgo.model.tla.*;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;
import pgo.util.SourceLocation;

import java.util.*;
import java.util.stream.Collectors;

public class ModularPlusCalCodeGenVisitor
		extends PlusCalStatementVisitor<List<PlusCalStatement>, RuntimeException> {
	private final DefinitionRegistry registry;
	private final TemporaryBinding readTemporaryBinding;
	private final TemporaryBinding writeTemporaryBinding;
	private final Map<UID, Set<UID>> labelsToVarReads;
	private final Map<UID, Set<UID>> labelsToVarWrites;
	private final Map<UID, PlusCalVariableDeclaration> arguments;
	private final Map<UID, TLAExpression> params;
	private final Map<UID, ModularPlusCalMapping> mappings;

	ModularPlusCalCodeGenVisitor(DefinitionRegistry registry, TemporaryBinding readTemporaryBinding,
	                             TemporaryBinding writeTemporaryBinding, Map<UID, Set<UID>> labelsToVarReads,
	                             Map<UID, Set<UID>> labelsToVarWrites, Map<UID, PlusCalVariableDeclaration> arguments,
	                             Map<UID, TLAExpression> params, Map<UID, ModularPlusCalMapping> mappings) {
		this.registry = registry;
		this.readTemporaryBinding = readTemporaryBinding;
		this.writeTemporaryBinding = writeTemporaryBinding;
		this.labelsToVarReads = labelsToVarReads;
		this.labelsToVarWrites = labelsToVarWrites;
		this.arguments = arguments;
		this.params = params;
		this.mappings = mappings;
	}

	private List<PlusCalStatement> substituteStatements(List<PlusCalStatement> statements) {
		List<PlusCalStatement> result = new ArrayList<>();
		for (PlusCalStatement statement : statements) {
			result.addAll(statement.accept(this));
		}
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalLabeledStatements plusCalLabeledStatements) throws RuntimeException {
		UID labelUID = plusCalLabeledStatements.getLabel().getUID();
		SourceLocation labelLocation = plusCalLabeledStatements.getLabel().getLocation();
		// translate the statements in this labeledStatements
		List<PlusCalStatement> statements = new ArrayList<>(substituteStatements(plusCalLabeledStatements.getStatements()));
		// write back the written values and clean up
		Set<UID> touchedVariables = new LinkedHashSet<>();
		if (labelsToVarReads.containsKey(labelUID)) {
			touchedVariables.addAll(labelsToVarReads.get(labelUID));
		}
		if (labelsToVarWrites.containsKey(labelUID)) {
			touchedVariables.addAll(labelsToVarWrites.get(labelUID));
		}
		for (UID varUID : touchedVariables) {
			// only write back written refs
			TLAExpression value = params.get(varUID);
			if (writeTemporaryBinding.lookup(varUID).isPresent() && value instanceof TLARef) {
				TLAGeneralIdentifier lhs = new TLAGeneralIdentifier(
						labelLocation,
						new TLAIdentifier(labelLocation, ((TLARef) value).getTarget()),
						Collections.emptyList());
				TLAGeneralIdentifier rhs = writeTemporaryBinding.lookup(varUID).get();
				statements.add(new PlusCalAssignment(
						labelLocation,
						Collections.singletonList(new PlusCalAssignmentPair(labelLocation, lhs, rhs))));
			}
			// clean up
			readTemporaryBinding.reuse(varUID);
			writeTemporaryBinding.reuse(varUID);
		}
		return Collections.singletonList(new PlusCalLabeledStatements(
				plusCalLabeledStatements.getLocation(),
				plusCalLabeledStatements.getLabel(),
				statements));
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalWhile plusCalWhile) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		TLAExpression condition = plusCalWhile.getCondition().accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalWhile(
				plusCalWhile.getLocation(),
				condition,
				substituteStatements(plusCalWhile.getBody())));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalIf plusCalIf) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		TLAExpression condition = plusCalIf.getCondition().accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalIf(
				plusCalIf.getLocation(),
				condition,
				substituteStatements(plusCalIf.getYes()),
				substituteStatements(plusCalIf.getNo())));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalEither plusCalEither) throws RuntimeException {
		return Collections.singletonList(new PlusCalEither(
				plusCalEither.getLocation(),
				plusCalEither.getCases().stream().map(this::substituteStatements).collect(Collectors.toList())));
	}

	private void assignmentHelper(SourceLocation location, TLAExpression lhs, TLAExpression rhs,
	                              List<PlusCalStatement> result) {
		TLAExpression transformedLHS = lhs.accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalAssignment(
				location,
				Collections.singletonList(new PlusCalAssignmentPair(location, transformedLHS, rhs))));
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalAssignment plusCalAssignment) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		List<PlusCalAssignmentPair> pairs = plusCalAssignment.getPairs();
		List<TLAExpression> rhsList = new ArrayList<>();
		if (pairs.size() > 1) {
			// read the RHS into temporary variables for use later so that parallel assignment works right
			for (PlusCalAssignmentPair pair : pairs) {
				SourceLocation location = pair.getLocation();
				TLAExpression rhs = pair.getRhs();
				TLAGeneralIdentifier tempVariable = readTemporaryBinding.declare(location, new UID(), "rhsRead");
				TLAExpression transformedRHS = rhs.accept(new TLAExpressionPlusCalCodeGenVisitor(
						registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
				result.add(new PlusCalAssignment(
						location,
						Collections.singletonList(new PlusCalAssignmentPair(location, tempVariable, transformedRHS))));
				rhsList.add(tempVariable);
			}
		} else {
			// otherwise, don't create temporary variable for cleaner code
			rhsList.add(pairs.get(0).getRhs().accept(new TLAExpressionPlusCalCodeGenVisitor(
					registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result)));
		}
		for (int i = 0; i < pairs.size(); i++) {
			PlusCalAssignmentPair pair = pairs.get(i);
			SourceLocation location = pair.getLocation();
			TLAExpression lhs = pair.getLhs();
			TLAExpression rhs = rhsList.get(i);
			if (!(lhs instanceof TLAGeneralIdentifier)) {
				assignmentHelper(location, lhs, rhs, result);
				continue;
			}
			UID varUID = registry.followReference(lhs.getUID());
			if (!arguments.containsKey(varUID)) {
				assignmentHelper(location, lhs, rhs, result);
				continue;
			}
			TLAExpression value = params.get(varUID);
			UID valueUID = registry.followReference(value.getUID());
			if (!mappings.containsKey(valueUID)) {
				assignmentHelper(location, lhs, rhs, result);
				continue;
			}
			TLAGeneralIdentifier variable = value instanceof TLARef
					? new TLAGeneralIdentifier(
					location,
					new TLAIdentifier(location, ((TLARef) value).getTarget()),
					Collections.emptyList())
					: (TLAGeneralIdentifier) value;
			ModularPlusCalMappingMacroWriteExpansionVisitor visitor =
					new ModularPlusCalMappingMacroWriteExpansionVisitor(
							readTemporaryBinding,
							writeTemporaryBinding,
							varUID,
							arguments.get(varUID).getName().getValue() + "Write",
							new TLAExpressionMappingMacroWriteExpansionVisitor(
									registry, readTemporaryBinding, writeTemporaryBinding, varUID, variable, rhs));
			for (PlusCalStatement statement : registry.findMappingMacro(mappings.get(valueUID).getTarget().getName()).getWriteBody()) {
				result.addAll(statement.accept(visitor));
			}
		}
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalReturn plusCalReturn) throws RuntimeException {
		return Collections.singletonList(new PlusCalReturn(plusCalReturn.getLocation()));
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalSkip plusCalSkip) throws RuntimeException {
		return Collections.singletonList(new PlusCalSkip(plusCalSkip.getLocation()));
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalCall plusCalCall) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		List<TLAExpression> args = new ArrayList<>();
		TLAExpressionPlusCalCodeGenVisitor visitor = new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result);
		for (TLAExpression argument : plusCalCall.getArguments()) {
			args.add(argument.accept(visitor));
		}
		result.add(new PlusCalCall(plusCalCall.getLocation(), plusCalCall.getTarget(), args));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalMacroCall macroCall) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalWith plusCalWith) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		List<PlusCalVariableDeclaration> declarations = new ArrayList<>();
		for (PlusCalVariableDeclaration declaration : plusCalWith.getVariables()) {
			declarations.add(new PlusCalVariableDeclaration(
					declaration.getLocation(),
					declaration.getName(),
					false,
					declaration.isSet(),
					declaration.getValue().accept(new TLAExpressionPlusCalCodeGenVisitor(
							registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding,
							result))));
		}
		result.add(new PlusCalWith(plusCalWith.getLocation(), declarations, substituteStatements(plusCalWith.getBody())));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalPrint plusCalPrint) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		TLAExpression expression = plusCalPrint.getValue().accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalPrint(plusCalPrint.getLocation(), expression));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalAssert plusCalAssert) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		TLAExpression expression = plusCalAssert.getCondition().accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalPrint(plusCalAssert.getLocation(), expression));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalAwait plusCalAwait) throws RuntimeException {
		List<PlusCalStatement> result = new ArrayList<>();
		TLAExpression condition = plusCalAwait.getCondition().accept(new TLAExpressionPlusCalCodeGenVisitor(
				registry, arguments, params, mappings, readTemporaryBinding, writeTemporaryBinding, result));
		result.add(new PlusCalAwait(plusCalAwait.getLocation(), condition));
		return result;
	}

	@Override
	public List<PlusCalStatement> visit(PlusCalGoto plusCalGoto) throws RuntimeException {
		return Collections.singletonList(plusCalGoto.copy());
	}

	@Override
	public List<PlusCalStatement> visit(ModularPlusCalYield modularPlusCalYield) throws RuntimeException {
		throw new Unreachable();
	}
}