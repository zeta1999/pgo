package pgo.model.tla;

import java.util.Vector;

import pcal.TLAToken;
import pgo.model.golang.Expression;
import pgo.model.golang.FunctionCall;
import pgo.model.golang.Statement;
import pgo.parser.TLAExprParser;
import pgo.trans.PGoTransException;
import pgo.trans.intermediate.PGoTransStageGoGen;

/**
 * Represents a set "{ ... }" in TLA. This should store what is in the set, and the set
 * notations for the set.
 *
 */
public class PGoTLASet extends PGoTLA {

	private Vector<PGoTLA> contents;

	public PGoTLASet(Vector<TLAToken> between, int line) throws PGoTransException {
		super(line);
		contents = new TLAExprParser(between, line).getResult();
	}

	public Vector<PGoTLA> getContents() {
		return contents;
	}

	protected Vector<Statement> toStatements() {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> contents = new Vector<>();
		for (PGoTLA ptla : this.getContents()) {
			contents.addAll(ptla.toStatements());
		}

		Vector<Expression> args = new Vector<>();
		for (Statement s : contents) {
			assert (s instanceof Expression);
			args.add((Expression) s);
		}

		PGoTransStageGoGen.go.getImports().addImport("mapset");
		// imports
		FunctionCall fc = new FunctionCall("mapset.NewSet", args);
		ret.addElement(fc);
		return ret;
	}

	public String toString() {
		String ret = "PGoTLASet (" + this.getLine() + "): {";
		for (PGoTLA p : contents) {
			ret += "(" + p.toString() + "), ";
		}
		return ret + "}";
	}
}
