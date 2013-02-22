package javac.util;

import javac.absyn.BinaryOp;
import javac.quad.BinOp;
import javac.quad.Call;
import javac.quad.Label;
import javac.quad.Oprand;
import javac.quad.Temp;
import javac.quad.TempOprand;
import javac.semantic.SException;
import javac.trans.Trans;
import javac.type.CHAR;
import javac.type.INT;
import javac.type.STRING;
import javac.type.Type;

public class ContBinaryExpr {
	public static TransExprCont getContBinaryExprRight(final Oprand tl,final BinaryOp op,final Type inferType,final Type lty,final Type rty){
		//TODO:tail recurse for other BinaryOp?
		if(op!=BinaryOp.PLUS)throw new SException("meow,meow...");
		return (new TransExprCont(){
			@Override
			public Oprand trans(Oprand tr) {
				Temp tmp=null;
				TempOprand ret=null;
				boolean isStrcat=(lty instanceof STRING || rty instanceof STRING||inferType instanceof STRING);
				if(op != BinaryOp.ASSIGN&&op!=BinaryOp.COMMA){
					tmp=Trans.hasComputed(tl,op,tr,isStrcat);
					if(tmp==null&&!isStrcat){
						tmp=new Temp();
						Trans.stackPtr.put(tmp,Trans.frameSize++);
						ret=Trans.Temp2TempOprand(tmp);
					}else if(!isStrcat) {
						ret=Trans.Temp2TempOprand(tmp);
						return ret;
					}
				}
				if(isStrcat){
					Temp[] params = new Temp[2];
					if(lty instanceof STRING){
						params[0] = Trans.mvTemp(tl, false, false, false);
					}else if(lty instanceof INT||lty instanceof CHAR){
						Temp[] convparams = new Temp[1];
						String funcname=(lty instanceof INT)?"_intToString":"_charToString";
						convparams[0]=Trans.mvTemp(tl, false, false, false);
						params[0]=Trans.hasComputed(funcname,convparams);
						if(params[0]==null){
							params[0]=new Temp();
							Trans.stackPtr.put(params[0], Trans.frameSize++);
							Trans.IR.add(new Call(new Label(funcname),convparams,params[0]));
							Trans.cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[0]);
						}
					}
					
					if(rty instanceof STRING){
						params[1] = Trans.mvTemp(tr, false, false, false);
					}else if(rty instanceof INT||rty instanceof CHAR){
						Temp[] convparams = new Temp[1];
						String funcname=(rty instanceof INT)?"_intToString":"_charToString";
						convparams[0]=Trans.mvTemp(tr, false, false, false);
						params[1]=Trans.hasComputed(funcname,convparams);
						if(params[1]==null){
							params[1]=new Temp();
							Trans.stackPtr.put(params[1],Trans.frameSize++);
							Trans.IR.add(new Call(new Label(funcname),convparams,params[1]));
							Trans.cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[1]);
						}
					}
					tmp=Trans.hasComputed("_strcat",params);
					if(tmp!=null){
						return Trans.Temp2TempOprand(tmp);
					}
					tmp=new Temp();
					Trans.stackPtr.put(tmp,Trans.frameSize++);
					ret=Trans.Temp2TempOprand(tmp);
					Trans.IR.add(new Call(new Label("_strcat"),params,ret.temp));
					Trans.cseTable.put("_strcat("+params[0].toSSA()+","+params[1].toSSA()+",",ret.temp);
				}else {
					Trans.IR.add(new BinOp(ret,tl,tr,op));
					if(tl!=null&&tl instanceof TempOprand&&tr!=null&&tr instanceof TempOprand){
						Temp t1=((TempOprand)tl).temp,t2=((TempOprand)tr).temp;
						Trans.cseTable.put(t1.toSSA()+op.toString()+t2.toSSA(),ret.temp);
						//TODO:what if tl instanceof Const?
					}
				}
				
				return ret;
			}
		});
	}
	public static TransExprCont getContBinaryExprLeft(final Oprand tr,final BinaryOp op,final Type inferType,final Type lty,final Type rty){
		//TODO:tail recurse for other BinaryOp?
		if(op!=BinaryOp.PLUS)throw new SException("meow,meow...");
		return (new TransExprCont(){
			@Override
			public Oprand trans(Oprand tl) {
				Temp tmp=null;
				TempOprand ret=null;
				boolean isStrcat=(lty instanceof STRING || rty instanceof STRING||inferType instanceof STRING);
				if(op != BinaryOp.ASSIGN&&op!=BinaryOp.COMMA){
					tmp=Trans.hasComputed(tl,op,tr,isStrcat);
					if(tmp==null&&!isStrcat){
						tmp=new Temp();
						Trans.stackPtr.put(tmp,Trans.frameSize++);
						ret=Trans.Temp2TempOprand(tmp);
					}else if(!isStrcat) {
						ret=Trans.Temp2TempOprand(tmp);
						return ret;
					}
				}
				if(isStrcat){
					Temp[] params = new Temp[2];
					if(lty instanceof STRING){
						params[0] = Trans.mvTemp(tl, false, false, false);
					}else if(lty instanceof INT||lty instanceof CHAR){
						Temp[] convparams = new Temp[1];
						String funcname=(lty instanceof INT)?"_intToString":"_charToString";
						convparams[0]=Trans.mvTemp(tl, false, false, false);
						params[0]=Trans.hasComputed(funcname,convparams);
						if(params[0]==null){
							params[0]=new Temp();
							Trans.stackPtr.put(params[0], Trans.frameSize++);
							Trans.IR.add(new Call(new Label(funcname),convparams,params[0]));
							Trans.cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[0]);
						}
					}
					
					if(rty instanceof STRING){
						params[1] = Trans.mvTemp(tr, false, false, false);
					}else if(rty instanceof INT||rty instanceof CHAR){
						Temp[] convparams = new Temp[1];
						String funcname=(rty instanceof INT)?"_intToString":"_charToString";
						convparams[0]=Trans.mvTemp(tr, false, false, false);
						params[1]=Trans.hasComputed(funcname,convparams);
						if(params[1]==null){
							params[1]=new Temp();
							Trans.stackPtr.put(params[1],Trans.frameSize++);
							Trans.IR.add(new Call(new Label(funcname),convparams,params[1]));
							Trans.cseTable.put(funcname+"("+convparams[0].toSSA()+",",params[1]);
						}
					}
					tmp=Trans.hasComputed("_strcat",params);
					if(tmp!=null){
						return Trans.Temp2TempOprand(tmp);
					}
					tmp=new Temp();
					Trans.stackPtr.put(tmp,Trans.frameSize++);
					ret=Trans.Temp2TempOprand(tmp);
					Trans.IR.add(new Call(new Label("_strcat"),params,ret.temp));
					Trans.cseTable.put("_strcat("+params[0].toSSA()+","+params[1].toSSA()+",",ret.temp);
				}else {
					Trans.IR.add(new BinOp(ret,tl,tr,op));
					if(tl!=null&&tl instanceof TempOprand&&tr!=null&&tr instanceof TempOprand){
						Temp t1=((TempOprand)tl).temp,t2=((TempOprand)tr).temp;
						Trans.cseTable.put(t1.toSSA()+op.toString()+t2.toSSA(),ret.temp);
						//TODO:what if tl instanceof Const?
					}
				}
				
				return ret;
			}
		});
	}
	

}
