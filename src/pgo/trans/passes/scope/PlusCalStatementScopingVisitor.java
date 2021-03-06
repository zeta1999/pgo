package pgo.trans.passes.scope;

import pgo.Unreachable;
import pgo.errors.IssueContext;
import pgo.model.mpcal.ModularPlusCalYield;
import pgo.model.pcal.*;
import pgo.model.tla.TLAExpression;
import pgo.model.tla.TLAGeneralIdentifier;
import pgo.model.tla.TLARef;
import pgo.modules.TLAModuleLoader;
import pgo.trans.intermediate.DefinitionRegistry;
import pgo.trans.passes.codegen.pluscal.RefMismatchIssue;

import java.util.List;
import java.util.Set;

public class PlusCalStatementScopingVisitor extends PlusCalStatementVisitor<Void, RuntimeException> {
	public interface TLAExpressionScopingVisitorFactory {
		TLAExpressionScopingVisitor create(IssueContext ctx, TLAScopeBuilder builder, DefinitionRegistry registry, TLAModuleLoader loader,
		                                   Set<String> moduleRecursionSet, boolean requireDefinedConstants);
	}

	private final IssueContext ctx;
	private final TLAScopeBuilder builder;
	private final DefinitionRegistry registry;
	private final TLAModuleLoader loader;
	private final Set<String> moduleRecursionSet;
	private final TLAExpressionScopingVisitorFactory factory;
	private boolean requireDefinedConstants;

	public PlusCalStatementScopingVisitor(IssueContext ctx, TLAScopeBuilder builder, DefinitionRegistry registry,
	                                      TLAModuleLoader loader, Set<String> moduleRecursionSet, boolean requireDefinedConstants) {
		this.ctx = ctx;
		this.builder = builder;
		this.registry = registry;
		this.loader = loader;
		this.moduleRecursionSet = moduleRecursionSet;
		this.factory = TLAExpressionScopingVisitor::new;
		this.requireDefinedConstants = requireDefinedConstants;
	}

	public PlusCalStatementScopingVisitor(IssueContext ctx, TLAScopeBuilder builder, DefinitionRegistry registry,
	                                      TLAModuleLoader loader, Set<String> moduleRecursionSet,
	                                      TLAExpressionScopingVisitorFactory factory, boolean requireDefinedConstants) {
		this.ctx = ctx;
		this.builder = builder;
		this.registry = registry;
		this.loader = loader;
		this.moduleRecursionSet = moduleRecursionSet;
		this.factory = factory;
		this.requireDefinedConstants = requireDefinedConstants;
	}

	static void verifyRefMatching(IssueContext ctx, List<PlusCalVariableDeclaration> params, List<TLAExpression> args) {
		for (int i = 0; i < params.size(); i++) {
			PlusCalVariableDeclaration param = params.get(i);
			TLAExpression arg = args.get(i);
			if ((arg instanceof TLARef && !param.isRef())) {
				ctx.error(new RefMismatchIssue(param, arg));
			}
		}
	}

	@Override
	public Void visit(PlusCalLabeledStatements plusCalLabeledStatements) throws RuntimeException {
		for (PlusCalStatement stmt : plusCalLabeledStatements.getStatements()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalWhile plusCalWhile) throws RuntimeException {
		plusCalWhile.getCondition().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		for (PlusCalStatement stmt : plusCalWhile.getBody()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalIf plusCalIf) throws RuntimeException {
		plusCalIf.getCondition().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		for (PlusCalStatement stmt : plusCalIf.getYes()) {
			stmt.accept(this);
		}
		for (PlusCalStatement stmt : plusCalIf.getNo()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalEither plusCalEither) throws RuntimeException {
		for (List<PlusCalStatement> list : plusCalEither.getCases()) {
			for (PlusCalStatement stmt : list) {
				stmt.accept(this);
			}
		}
		return null;
	}

	@Override
	public Void visit(PlusCalAssignment plusCalAssignment) throws RuntimeException {
		for (PlusCalAssignmentPair pair : plusCalAssignment.getPairs()) {
			pair.getLhs().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
			pair.getRhs().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		}
		return null;
	}

	@Override
	public Void visit(PlusCalReturn plusCalReturn) throws RuntimeException {
		return null;
	}

	@Override
	public Void visit(PlusCalSkip plusCalSkip) throws RuntimeException {
		return null;
	}

	@Override
	public Void visit(PlusCalCall plusCalCall) throws RuntimeException {
		PlusCalProcedure procedure = registry.findProcedure(plusCalCall.getTarget());
		if (procedure != null && procedure.getParams().size() != plusCalCall.getArguments().size()) {
			ctx.error(new ProcedureCallArgumentCountMismatchIssue(procedure, plusCalCall));
		} else if (procedure != null) {
			verifyRefMatching(ctx, procedure.getParams(), plusCalCall.getArguments());
		} else {
			ctx.error(new ProcedureNotFoundIssue(plusCalCall, plusCalCall.getTarget()));
		}

		for (TLAExpression expr : plusCalCall.getArguments()) {
			expr.accept(factory.create(ctx, builder, null, null, null, false));
		}
		return null;
	}

	@Override
	public Void visit(PlusCalMacroCall macroCall) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(PlusCalWith plusCalWith) throws RuntimeException {
		TLAScopeBuilder nested = builder.makeNestedScope();
		for(PlusCalVariableDeclaration decl : plusCalWith.getVariables()) {
			decl.getValue().accept(factory.create(ctx, nested, registry, loader, moduleRecursionSet, requireDefinedConstants));
			nested.defineLocal(decl.getName().getValue(), decl.getUID());
			registry.addLocalVariable(decl.getUID());
		}

		for (PlusCalStatement stmt : plusCalWith.getBody()) {
			stmt.accept(new PlusCalStatementScopingVisitor(ctx, nested, registry, loader, moduleRecursionSet, factory, requireDefinedConstants));
		}
		return null;
	}

	@Override
	public Void visit(PlusCalPrint plusCalPrint) throws RuntimeException {
		plusCalPrint.getValue().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		return null;
	}

	@Override
	public Void visit(PlusCalAssert plusCalAssert) throws RuntimeException {
		plusCalAssert.getCondition().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		return null;
	}

	@Override
	public Void visit(PlusCalAwait plusCalAwait) throws RuntimeException {
		plusCalAwait.getCondition().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		return null;
	}

	@Override
	public Void visit(PlusCalGoto plusCalGoto) throws RuntimeException {
		builder.reference(plusCalGoto.getTarget(), plusCalGoto.getUID());
		return null;
	}

	@Override
	public Void visit(ModularPlusCalYield modularPlusCalYield) throws RuntimeException {
		modularPlusCalYield.getExpression().accept(factory.create(ctx, builder, registry, loader, moduleRecursionSet, requireDefinedConstants));
		return null;
	}
}
