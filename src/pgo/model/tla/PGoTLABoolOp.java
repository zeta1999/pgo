package pgo.model.tla;

/**
 * Represents a comparator or a binary boolean operation in TLA.
 *
 */
public class PGoTLABoolOp extends PGoTLA {

	private String token;

	private PGoTLA left;

	private PGoTLA right;

	public PGoTLABoolOp(String tok, PGoTLA prev, PGoTLA next, int line) {
		super(line);
		if (tok.equals("#") || tok.equals("/=")) {
			token = "!=";
		} else if (tok.equals("/\\")) {
			this.token = "&&";
		} else if (tok.equals("\\/")) {
			this.token = "||";
		} else {
			token = tok;
		}
		left = prev;
		right = next;
	}

	public String getToken() {
		return token;
	}

	public PGoTLA getLeft() {
		return left;
	}

	public PGoTLA getRight() {
		return right;
	}

	public String toString() {
		return "PGoTLAComp (" + this.getLine() + "): (" + left.toString() + ") " + token + " (" + right.toString() + ")";
	}
}