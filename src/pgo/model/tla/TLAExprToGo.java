package pgo.model.tla;

import java.util.Vector;

import pgo.model.golang.*;
import pgo.model.intermediate.PGoCollectionType;
import pgo.model.intermediate.PGoCollectionType.PGoSet;
import pgo.model.intermediate.PGoPrimitiveType.PGoDecimal;
import pgo.model.intermediate.PGoPrimitiveType.PGoNatural;
import pgo.model.intermediate.PGoPrimitiveType.PGoNumber;
import pgo.model.intermediate.PGoType;
import pgo.model.intermediate.PGoVariable;
import pgo.trans.PGoTransException;
import pgo.trans.intermediate.PGoTempData;

/**
 * Converts the TLA ast generated by the TLAExprParser into GoAST
 *
 */
public class TLAExprToGo {

	private Vector<Statement> stmts;
	// the Go program's imports
	private Imports imports;
	// the intermediate data; includes information about the type of variables
	private PGoTempData data;

	public TLAExprToGo(Vector<PGoTLA> tla, Imports imports, PGoTempData data) throws PGoTransException {
		stmts = new Vector<>();
		this.imports = imports;
		this.data = data;
		convert(tla);
	}

	public TLAExprToGo(PGoTLA tla, Imports imports, PGoTempData data) throws PGoTransException {
		this.imports = imports;
		this.data = data;
		stmts = convert(tla);
	}

	public SimpleExpression toSimpleExpression() {
		// TODO Auto-generated method stub
		return null;
	}

	public Vector<Statement> getStatements() {
		return stmts;
	}

	/**
	 * Takes PGoTLA ast tree and converts it to Go statement
	 * 
	 * TODO probably want to take the tokens into a class TLAExprToGo. Then
	 * support things like getEquivStatement() to get the equivalent go expr to
	 * refer to the equivalent data in the pluscal, and getInit() to get any
	 * initialization code to generate that data. Constructor of this class may
	 * need to know what local variable names are available
	 * 
	 * @param ptla
	 * @throws PGoTransException
	 *             if there is a typing inconsistency
	 */
	private void convert(Vector<PGoTLA> ptla) throws PGoTransException {
		for (PGoTLA tla : ptla) {
			// check type consistency
			new TLAExprToType(tla, data);
			stmts.addAll(tla.convert(this));
		}
	}

	private Vector<Statement> convert(PGoTLA tla) throws PGoTransException {
		new TLAExprToType(tla, data);
		return tla.convert(this);
	}

	/**
	 * Convert the TLA expression to a Go AST, while also adding the correct
	 * imports.
	 * 
	 * @param tla
	 *            the TLA expression
	 */
	protected Vector<Statement> translate(PGoTLAArray tla) {
		// TODO (issue #5, 23)
		return new Vector<>();
	}

	protected Vector<Statement> translate(PGoTLABool tla) {
		Vector<Statement> ret = new Vector<>();
		ret.add(new Token(String.valueOf(tla.getVal())));
		return ret;
	}

	protected Vector<Statement> translate(PGoTLABoolOp tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> leftRes = convert(tla.getLeft());
		Vector<Statement> rightRes = convert(tla.getRight());

		// comparators operations should just be a single SimpleExpression
		assert (leftRes.size() == 1);
		assert (rightRes.size() == 1);
		assert (leftRes.get(0) instanceof Expression);
		assert (rightRes.get(0) instanceof Expression);

		// we already know the types are consistent
		PGoType leftType = new TLAExprToType(tla.getLeft(), data).getType();
		if (leftType instanceof PGoSet) {
			imports.addImport("mapset");
			Vector<Expression> leftExp = new Vector<>();
			leftExp.add((Expression) leftRes.get(0));

			switch (tla.getToken()) {
			case "#":
			case "/=":
				Vector<Expression> toks = new Vector<>();
				toks.add(new Token("!"));
				toks.add(new FunctionCall("Equal", leftExp, (Expression) rightRes.get(0)));
				ret.add(new SimpleExpression(toks));
				return ret;
			case "=":
			case "==":
				ret.add(new FunctionCall("Equal", leftExp, (Expression) rightRes.get(0)));
				return ret;
			default:
				assert false;
				return null;
			}
		}

		String tok = tla.getToken();
		switch (tok) {
		case "#":
		case "/=":
			tok = "!=";
			break;
		case "/\\":
		case "\\land":
			tok = "&&";
			break;
		case "\\/":
		case "\\lor":
			tok = "||";
			break;
		case "=<":
		case "\\leq":
			tok = "<=";
			break;
		case "\\geq":
			tok = ">=";
			break;
		case "=":
			tok = "==";
			break;
		}

		Expression lhs = (Expression) leftRes.get(0), rhs = (Expression) rightRes.get(0);
		// if we are comparing number types we may need to do type conversion
		if (leftType instanceof PGoNumber) {
			PGoType rightType = new TLAExprToType(tla.getRight(), data).getType();
			PGoType convertedType = TLAExprToType.compatibleType(leftType, rightType);
			assert (convertedType != null);
			// cast if not plain number
			if (!leftType.equals(convertedType) && !(tla.getLeft() instanceof PGoTLANumber)) {
				lhs = new TypeConversion(convertedType, lhs);
			} else if (!rightType.equals(convertedType) && !(tla.getRight() instanceof PGoTLANumber)) {
				// only one of the left or right needs to be cast
				rhs = new TypeConversion(convertedType, rhs);
			}
		}

		Vector<Expression> toks = new Vector<Expression>();
		toks.add(lhs);
		toks.add(new Token(" " + tok + " "));
		toks.add(rhs);

		ret.add(new SimpleExpression(toks));
		return ret;
	}

	protected Vector<Statement> translate(PGoTLAFunction tla) {
		// TODO (issue #23)
		return new Vector<>();
	}

	protected Vector<Statement> translate(PGoTLAGroup tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> inside = convert(tla.getInner());

		assert (inside.size() == 1);
		assert (inside.get(0) instanceof Expression);

		ret.add(new Group((Expression) inside.get(0)));

		return ret;
	}

	protected Vector<Statement> translate(PGoTLANumber tla) {
		Vector<Statement> ret = new Vector<>();
		ret.add(new Token(tla.getVal()));
		return ret;
	}

	protected Vector<Statement> translate(PGoTLASequence tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> startRes = convert(tla.getStart());
		Vector<Statement> endRes = convert(tla.getEnd());

		// comparators operations should just be a single Expression
		assert (startRes.size() == 1);
		assert (endRes.size() == 1);
		assert (startRes.get(0) instanceof Expression);
		assert (endRes.get(0) instanceof Expression);

		Vector<Expression> args = new Vector<>();
		Expression start = (Expression) startRes.get(0), end = (Expression) endRes.get(0);

		// we may need to convert natural to int
		PGoType startType = new TLAExprToType(tla.getStart(), data).getType();
		PGoType endType = new TLAExprToType(tla.getEnd(), data).getType();
		// plain numbers are never naturals (int or float only), so we don't
		// need to check if the exprs are plain numbers
		if (startType instanceof PGoNatural) {
			start = new TypeConversion("int", start);
		}
		if (endType instanceof PGoNatural) {
			end = new TypeConversion("int", end);
		}
		args.add(start);
		args.add(end);

		FunctionCall fc = new FunctionCall("pgoutil.Sequence", args);
		ret.add(fc);

		this.imports.addImport("pgoutil");
		return ret;
	}

	protected Vector<Statement> translate(PGoTLASet tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> contents = new Vector<>();
		for (PGoTLA ptla : tla.getContents()) {
			contents.addAll(convert(ptla));
		}

		Vector<Expression> args = new Vector<>();
		for (Statement s : contents) {
			assert (s instanceof Expression);
			args.add((Expression) s);
		}

		FunctionCall fc = new FunctionCall("mapset.NewSet", args);
		ret.addElement(fc);

		this.imports.addImport("mapset");
		return ret;
	}

	protected Vector<Statement> translate(PGoTLASetOp tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> leftRes = convert(tla.getLeft());
		Vector<Statement> rightRes = convert(tla.getRight());

		// lhs and rhs should each be a single Expression
		assert (leftRes.size() == 1);
		assert (rightRes.size() == 1);
		assert (leftRes.get(0) instanceof Expression);
		assert (rightRes.get(0) instanceof Expression);

		Vector<Expression> lhs = new Vector<>();
		lhs.add((Expression) leftRes.get(0));
		Expression rightSet = (Expression) rightRes.get(0);

		Vector<Expression> exp = new Vector<>();
		String funcName = null;
		// Map the set operation to the mapset function. \\notin does not have a
		// corresponding function and is handled separately.
		switch (tla.getToken()) {
		case "\\cup":
		case "\\union":
			funcName = "Union";
			break;
		case "\\cap":
		case "\\intersect":
			funcName = "Intersect";
			break;
		case "\\in":
			funcName = "Contains";
			break;
		case "\\notin":
			funcName = "NotIn";
			break;
		case "\\subseteq":
			funcName = "IsSubset";
			break;
		case "\\":
			funcName = "Difference";
			break;
		default:
			assert false;
		}

		if (funcName.equals("NotIn")) {
			exp.add(new Token("!"));
			funcName = "Contains";
		}
		// rightSet is the object because lhs can be an element (e.g. in
		// Contains)
		FunctionCall fc = new FunctionCall(funcName, lhs, rightSet);
		exp.add(fc);
		ret.add(new SimpleExpression(exp));
		this.imports.addImport("mapset");
		return ret;
	}

	protected Vector<Statement> translate(PGoTLASimpleArithmetic tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		Vector<Statement> leftRes = convert(tla.getLeft());
		Vector<Statement> rightRes = convert(tla.getRight());

		// arithmetic operations should just be a single SimpleExpression
		assert (leftRes.size() == 1);
		assert (rightRes.size() == 1);
		assert (leftRes.get(0) instanceof Expression);
		assert (rightRes.get(0) instanceof Expression);

		Expression lhs = (Expression) leftRes.get(0), rhs = (Expression) rightRes.get(0);
		PGoType leftType = new TLAExprToType(tla.getLeft(), data).getType();
		PGoType rightType = new TLAExprToType(tla.getRight(), data).getType();
		if (tla.getToken().equals("^")) {
			this.imports.addImport("math");
			Vector<Expression> params = new Vector<>();
			// math.Pow takes float64s; convert if needed
			if (!(tla.getLeft() instanceof PGoTLANumber || leftType instanceof PGoDecimal)) {
				lhs = new TypeConversion("float64", lhs);
			}
			if (!(tla.getRight() instanceof PGoTLANumber || rightType instanceof PGoDecimal)) {
				rhs = new TypeConversion("float64", rhs);
			}

			params.add(lhs);
			params.add(rhs);
			FunctionCall fc = new FunctionCall("math.Pow", params);
			ret.add(fc);
		} else {
			PGoType convertedType = TLAExprToType.compatibleType(leftType, rightType);
			assert (convertedType != null);
			if (!(tla.getLeft() instanceof PGoTLANumber || leftType.equals(convertedType))) {
				lhs = new TypeConversion(convertedType, lhs);
			} else if (!(tla.getRight() instanceof PGoTLANumber || rightType.equals(convertedType))) {
				rhs = new TypeConversion(convertedType, rhs);
			}
			Vector<Expression> toks = new Vector<>();
			toks.add(lhs);
			toks.add(new Token(" " + tla.getToken() + " "));
			toks.add(rhs);

			ret.add(new SimpleExpression(toks));
		}
		return ret;
	}

	protected Vector<Statement> translate(PGoTLAString tla) {
		Vector<Statement> ret = new Vector<>();
		ret.add(new Token(tla.getString()));
		return ret;
	}

	protected Vector<Statement> translate(PGoTLAUnary tla) throws PGoTransException {
		Vector<Statement> ret = new Vector<>();

		switch (tla.getToken()) {
		case "~":
		case "\\lnot":
		case "\\neg":
			Vector<Statement> stmts = convert(tla.getArg());
			assert (stmts.size() == 1);
			assert (stmts.get(0) instanceof Expression);
			Vector<Expression> exp = new Vector<>();
			exp.add(new Token("!"));
			exp.add((Expression) stmts.get(0));
			ret.add(new SimpleExpression(exp));
			break;
		case "UNION":
			stmts = convert(tla.getArg());
			assert (stmts.size() == 1);
			assert (stmts.get(0) instanceof Expression);
			FunctionCall fc = new FunctionCall("pgoutil.EltUnion", new Vector<Expression>() {
				{
					add((Expression) stmts.get(0));
				}
			});
			this.imports.addImport("pgoutil");
			ret.add(fc);
			break;
		case "SUBSET":
			stmts = convert(tla.getArg());
			FunctionCall fc1 = new FunctionCall("PowerSet", new Vector<>(), (Expression) stmts.get(0));
			this.imports.addImport("mapset");
			ret.add(fc1);
			break;
		// these operations are of the form OP x \in S : P(x)
		case "CHOOSE":
			PGoTLASuchThat st = (PGoTLASuchThat) tla.getArg();
			assert (st.getSets().size() == 1);
			// the set S
			Vector<Statement> setExpr = convert(st.getSets().get(0).getRight());
			// the variable x
			Vector<Statement> varExpr = convert(st.getSets().get(0).getLeft());
			// We need to add typing data to avoid TLAExprToType complaining
			// about untyped variables
			PGoTempData temp = new PGoTempData(data);
			for (PGoTLASetOp set : st.getSets()) {
				// TODO handle stuff like << x, y >> \in S \X T
				assert (set.getLeft() instanceof PGoTLAVariable);
				PGoTLAVariable var = (PGoTLAVariable) set.getLeft();
				PGoType containerType = new TLAExprToType(set.getRight(), data).getType();
				assert (containerType instanceof PGoSet);
				PGoType eltType = ((PGoSet) containerType).getElementType();
				temp.getLocals().put(var.getName(), PGoVariable.convert(var.getName(), eltType));
			}
			Vector<Statement> pred = new TLAExprToGo(st.getExpr(), imports, temp).getStatements();

			assert (setExpr.size() == 1);
			assert (varExpr.size() == 1);
			assert (pred.size() == 1);
			assert (setExpr.get(0) instanceof Expression);
			assert (varExpr.get(0) instanceof Expression);
			assert (pred.get(0) instanceof Expression);

			Expression varName = (Expression) varExpr.get(0);
			// most expressions can't be used as the variable (only stuff like
			// tuples) so this should be one line
			assert (varName.toGo().size() == 1);

			// create the anonymous function for the predicate
			// since there are no complex assignments, the predicate should
			// be a single Expression
			assert (pred.size() == 1);
			assert (pred.get(0) instanceof Expression);
			// go func: Choose(P interface{}, S mapset.Set) interface{}
			// (P is predicate)
			// P = func(varType) bool { return pred }
			AnonymousFunction P = new AnonymousFunction(PGoType.inferFromGoTypeName("bool"),
					// TODO (issue 28) deal with tuples as variable declaration
					new Vector<ParameterDeclaration>() {
						{
							add(new ParameterDeclaration(varName.toGo().get(0),
									new TLAExprToType(tla, data).getType()));
						}
					},
					new Vector<>(),
					new Vector<Statement>() {
						{
							add(new Return((Expression) pred.get(0)));
						}
					});

			Vector<Expression> chooseFuncParams = new Vector<>();
			chooseFuncParams.add(P);
			chooseFuncParams.add((Expression) setExpr.get(0));

			this.imports.addImport("pgoutil");
			FunctionCall choose = new FunctionCall("pgoutil.Choose", chooseFuncParams);
			ret.add(choose);
			break;
		case "\\E":
		case "\\A":
			st = (PGoTLASuchThat) tla.getArg();

			temp = new PGoTempData(data);
			for (PGoTLASetOp set : st.getSets()) {
				// TODO handle stuff like << x, y >> \in S \X T
				assert (set.getLeft() instanceof PGoTLAVariable);
				PGoTLAVariable var = (PGoTLAVariable) set.getLeft();
				PGoType containerType = new TLAExprToType(set.getRight(), data).getType();
				assert (containerType instanceof PGoSet);
				PGoType eltType = ((PGoSet) containerType).getElementType();
				temp.getLocals().put(var.getName(), PGoVariable.convert(var.getName(), eltType));
			}
			pred = new TLAExprToGo(st.getExpr(), imports, temp).getStatements();

			Vector<Statement> setExprs = new Vector<>(), varExprs = new Vector<>();
			for (PGoTLASetOp setOp : st.getSets()) {
				varExprs.add(convert(setOp.getLeft()).get(0));
				setExprs.add(convert(setOp.getRight()).get(0));
			}
			// create the anonymous function for the predicate
			// since there are no complex assignments, the predicate should
			// be a single Expression
			assert (pred.size() == 1);
			assert (pred.get(0) instanceof Expression);
			// go func: Choose(P interface{}, S mapset.Set) interface{}
			// (P is predicate)
			// P = func(varType, varType...) bool { return pred }
			P = new AnonymousFunction(PGoType.inferFromGoTypeName("bool"),
					// TODO (issue 28) deal with tuples as variable declaration
					new Vector<ParameterDeclaration>() {
						{
							// var[i] \in set[i]
							for (int i = 0; i < setExprs.size(); i++) {
								PGoType setType = new TLAExprToType(st.getSets().get(i).getRight(), data)
										.getType();
								PGoType varType = ((PGoCollectionType) setType).getElementType();
								add(new ParameterDeclaration(varExprs.get(i).toGo().get(0),
										varType));
							}
						}
					},
					new Vector<>(),
					new Vector<Statement>() {
						{
							add(new Return((Expression) pred.get(0)));
						}
					});

			Vector<Expression> funcParams = new Vector<>();
			funcParams.add(P);
			for (Statement s : setExprs) {
				funcParams.add((Expression) s);
			}

			this.imports.addImport("pgoutil");
			FunctionCall call = new FunctionCall((tla.getToken().equals("\\E") ? "pgoutil.Exists" : "pgoutil.ForAll"),
					funcParams);
			ret.add(call);
			break;
		}
		return ret;
	}

	protected Vector<Statement> translate(PGoTLAVariable tla) {
		Vector<Statement> ret = new Vector<>();
		ret.add(new Token(String.valueOf(tla.getName())));
		return ret;
	}

	protected Vector<Statement> translate(PGoTLASuchThat tla) {
		// This compiles differently based on context, so we should deal with
		// translating this when we have the appropriate context.
		assert false;
		return null;
	}
}
