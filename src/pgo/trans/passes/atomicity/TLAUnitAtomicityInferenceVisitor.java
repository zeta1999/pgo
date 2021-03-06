package pgo.trans.passes.atomicity;

import pgo.Unreachable;
import pgo.model.tla.*;
import pgo.trans.passes.atomicity.TLAExpressionValueAtomicityInferenceVisitor;

public class TLAUnitAtomicityInferenceVisitor extends TLAUnitVisitor<Void, RuntimeException> {
	protected TLAExpressionValueAtomicityInferenceVisitor visitor;

	public TLAUnitAtomicityInferenceVisitor(TLAExpressionValueAtomicityInferenceVisitor visitor) {
		this.visitor = visitor;
	}

	@Override
	public Void visit(TLAInstance pGoTLAInstance) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAFunctionDefinition pGoTLAFunctionDefinition) throws RuntimeException {
		pGoTLAFunctionDefinition.getFunction().accept(visitor);
		return null;
	}

	@Override
	public Void visit(TLAOperatorDefinition pGoTLAOperator) throws RuntimeException {
		pGoTLAOperator.getBody().accept(visitor);
		return null;
	}

	@Override
	public Void visit(TLATheorem pGoTLATheorem) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAModule pGoTLAModule) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAVariableDeclaration pGoTLAVariableDeclaration) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAConstantDeclaration TLAConstantDeclaration) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAModuleDefinition pGoTLAModuleDefinition) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(TLAAssumption TLAAssumption) throws RuntimeException {
		throw new Unreachable();
	}
}
