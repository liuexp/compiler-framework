package javac.absyn;

import java.util.Arrays;
import java.util.LinkedList;

import javac.quad.BinOp;
import javac.quad.Branch;
import javac.quad.Call;
import javac.quad.Const;
import javac.quad.Label;
import javac.quad.LabelQuad;
import javac.quad.Mem;
import javac.quad.Move;
import javac.quad.Temp;
import javac.quad.TempOprand;
import javac.symbol.Symbol;
import javac.trans.Trans;
import javac.type.CHAR;
import javac.type.INT;
import javac.type.STRING;
import javac.type.Type;
import javac.util.ContBinaryExpr;
import javac.util.NullVisitCont;
import javac.util.Position;
import javac.util.TransExprCont;
import javac.util.VisitExprCont;

public class BinaryExpr extends Expr {
	
	public Expr l, r;
	public BinaryOp op;
	public static LinkedList<VisitExprCont> tailRecurse;
	
	static boolean canTileLeft(Expr a,Expr b,BinaryOp op){
    	BinaryExpr l=null,r=null;
    	if(op!=BinaryOp.PLUS&&op!=BinaryOp.MULTIPLY)return false;
    	if(a instanceof BinaryExpr){
    		l=(BinaryExpr)a;
    	}
    	if(b instanceof BinaryExpr){
    		r=(BinaryExpr)b;
    	}
    	if(l!=null&&l.op==op&&!l.r.hasSideEffect){
    		return true;
    	}else
    		return false;
    }
    static boolean canTileRight(Expr a,Expr b,BinaryOp op){
    	BinaryExpr l=null,r=null;
    	if(op!=BinaryOp.PLUS&&op!=BinaryOp.MULTIPLY)return false;
    	if(a instanceof BinaryExpr){
    		l=(BinaryExpr)a;
    	}
    	if(b instanceof BinaryExpr){
    		r=(BinaryExpr)b;
    	}
    	if(r!=null&&r.op==op){
    		return true;
    	}else
    		return false;
    }
    public boolean tryRotateLeft2Right(){
    	if(!(l instanceof BinaryExpr))return false;
    	Expr lhs=((BinaryExpr)l).l,rhs=((BinaryExpr)l).r;
    	Type infT=((BinaryExpr)l).inferType;
    	BinaryOp lop=((BinaryExpr)l).op;
    	if(canTileLeft(lhs,rhs,op)&&lhs.depth>rhs.depth+5){//lhs.size>rhs.size+1){
			Expr ll=((BinaryExpr)lhs).l;
			Expr lr=((BinaryExpr)lhs).r;
			BinaryOp lhsop=((BinaryExpr)lhs).op;
			Expr newr=new BinaryExpr(lr.pos,lr,lop,rhs);
			newr.inferType=infT;
			l=new BinaryExpr(((BinaryExpr)l).pos,ll,lhsop,newr,lhs.depth<rhs.depth+50);//here false works good for structured data and true works for unstructured data
			l.inferType=infT;
			return true;
		}
    	return false;
    }

    static javac.util.Position getPos(int line, int column) {
        return javac.util.Position.valueOf(line, column);
    }
    
	public BinaryExpr(Position pos, Expr lhs, BinaryOp op, Expr rhs) {
		super(pos);
		if(lhs instanceof FunctionCall&&rhs instanceof StringLiteral&&op==BinaryOp.EQ&&((FunctionCall)lhs).expr.toString().equals("substring")&&((StringLiteral)rhs).s.length()==1){
			FunctionCall f=(FunctionCall)lhs;
			if(f.args.exprList.size()>=3){
				Expr tmp=f.args.exprList.get(2);
				if(tmp.isConst&&tmp.value==1){
					rhs=new CharLiteral(rhs.pos,(((StringLiteral)rhs).s).charAt(0));
					lhs=new SubscriptPostfix(lhs.pos,f.args.exprList.get(0),f.args.exprList.get(1));
				}
			}
		}
			l = lhs;
			r = rhs;
			this.op = op;
			Type lty=l.inferType;
			Type rty=r.inferType;
			if((lty instanceof STRING || rty instanceof STRING)&&op==BinaryOp.PLUS) inferType = STRING.getInstance();
			else if((lty instanceof CHAR || lty instanceof INT) && (rty instanceof CHAR || rty instanceof INT)) inferType = INT.getInstance();
			
			int cut=4,cur=0;
			if(cut!=0){while(tryRotateLeft2Right()&&cur<cut)cur++;}
			this.hasSideEffect=l.hasSideEffect||r.hasSideEffect||op==BinaryOp.ASSIGN;
		size=l.size+r.size+1;
		depth=max(l.depth,r.depth)+1;
		isConst=l.isConst&&r.isConst&&inferType instanceof INT;
		if(isConst){
			value=calc(op,l.value,r.value);
		}
	}
	public int calc(BinaryOp o, int a, int b) {
		switch(o){
		case MULTIPLY:
			return a*b;
		case DIVIDE:
			return a/b;
		case MODULO:
			return a%b;
		case MINUS:
			return a-b;
		case AND:
			return (a!=0&&b!=0)?1:0;
		case OR:
			return (a!=0||b!=0)?1:0;
		case PLUS:
			return a+b;
		case EQ:
			return a==b?1:0;
		case NEQ:
			return a!=b?1:0;
		case LESS:
			return a<b?1:0;
		case LESS_EQ:
			return a<=b?1:0;
		case GREATER:
			return a>b?1:0;
		case GREATER_EQ:
			return a>=b?1:0;
		case ASSIGN:
			error("non-lvalue");
			return 0;
		case COMMA:
			return b;
		default:
			error(this.toString()+" : looks like 2012 stuff.");
			return 0;
		}
	}
	private Integer max(Integer a, Integer b) {
		// TODO Auto-generated method stub
		return a>b?a:b;
	}
	//TODO:depth instead of size, and only try to rotate moderate depth difference
	public BinaryExpr(Position pos, Expr lhs, BinaryOp op, Expr rhs,boolean tile) {
		super(pos);
			l = lhs;
			r = rhs;
			this.op = op;
			Type lty=l.inferType;
			Type rty=r.inferType;
			if(lty instanceof STRING || rty instanceof STRING) inferType = STRING.getInstance();
			else if((lty instanceof CHAR || lty instanceof INT) && (rty instanceof CHAR || rty instanceof INT)) inferType = INT.getInstance();

			int cut=4,cur=0;
			if(!tile)return;
			while(tryRotateLeft2Right()&&cur<cut)cur++;
			this.hasSideEffect=l.hasSideEffect||r.hasSideEffect||op==BinaryOp.ASSIGN;
		size=l.size+r.size+1;
		depth=max(l.depth,r.depth)+1;
		isConst=l.isConst&&r.isConst&&inferType instanceof INT;
		if(isConst){
			value=calc(op,l.value,r.value);
		}
	}

	@Override
	public void accept(NodeVisitor visitor) {
		if(Trans.canTailRecurse(this)){
			if(l instanceof BinaryExpr&&Trans.nearLeaf(r)){
				((BinaryExpr)l).acceptUnBalanced(visitor);
				r.accept(visitor);
				visitor.visit(this);
				return ;
			}else if(r instanceof BinaryExpr&&Trans.nearLeaf(l)){
				l.accept(visitor);
				((BinaryExpr)r).acceptUnBalanced(visitor);
				visitor.visit(this);
				return ;
			}
		}
		l.accept(visitor);
		r.accept(visitor);
		visitor.visit(this);
	}
	//TODO:tail recurse
	public void acceptUnBalanced(NodeVisitor visitor) {
		if(tailRecurse==null)tailRecurse=new LinkedList<VisitExprCont> ();
		tailRecurse.add(new NullVisitCont(){ });
		Expr Nextexpr=this;
		while(Nextexpr instanceof BinaryExpr){
			final Expr ll = ((BinaryExpr)Nextexpr).l,rr=((BinaryExpr)Nextexpr).r;
			final BinaryExpr thisExpr=(BinaryExpr)Nextexpr;
			if(!Trans.canTailRecurse((BinaryExpr)Nextexpr))break;
			if((!Trans.isLeaf(ll)||!Trans.isLeaf(rr))){
				if(!Trans.isLeaf(ll)&&Trans.nearLeaf(rr)){
					rr.accept(visitor);
					tailRecurse.add(new VisitExprCont(){
						
						@Override
						public void visit(final NodeVisitor visitor){
							
							visitor.visit(thisExpr);
						}
					});
					if(ll instanceof BinaryExpr)Nextexpr=(BinaryExpr) ll;
					else {Nextexpr=ll;break;}
				}else if(!Trans.isLeaf(rr)&&Trans.nearLeaf(ll)){
					ll.accept(visitor);
					tailRecurse.add(new VisitExprCont(){
						@Override
						public void visit(final NodeVisitor visitor){
							
							visitor.visit(thisExpr);
						}
					});
					if(rr instanceof BinaryExpr)Nextexpr=(BinaryExpr) rr;
					else  {Nextexpr=rr;break;}
				}else{
					error("meow meow meow!");
				}
			}else{
				break;
			}
		}
		Nextexpr.accept(visitor);
		while(!tailRecurse.peekLast().isNull()){
			tailRecurse.pollLast().visit(visitor);
		}
		tailRecurse.pollLast();
	}
	private void error(String string) {
		// TODO Auto-generated method stub
		throw new RuntimeException("mie@BinaryExpr");
	}
}
