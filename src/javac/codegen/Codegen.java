package javac.codegen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javac.absyn.BinaryOp;
import javac.block.BasicBlock;
import javac.linearScan.LinearScan;
import javac.quad.BinOp;
import javac.quad.Branch;
import javac.quad.Call;
import javac.quad.Const;
import javac.quad.Jump;
import javac.quad.Label;
import javac.quad.LabelAddress;
import javac.quad.LabelQuad;
import javac.quad.Mem;
import javac.quad.Move;
import javac.quad.Oprand;
import javac.quad.Quad;
import javac.quad.Return;
import javac.quad.Temp;
import javac.quad.TempOprand;
import javac.quad.UnaryOp;
import javac.semantic.SException;

//FIXME:this file is deprecated.
public class Codegen {
	
	public Label name;
	public Temp [] argv;
	public LinkedList<Quad> body;
	
	public int frameSize;
	public HashMap<Temp, Integer> stackPtr;
	//public HashMap<Temp,String> temp2reg;//for graph coloring
	public HashMap<Temp,String> tempInReg;
	public HashMap<Temp,Integer> tempInMem;
	public HashMap<String,HashSet<Temp> > reg2temp;//register descriptor
	public LinkedList<String> freeRegs;
	public LinkedList<BasicBlock> blocks;
	
	public int getLocation(Temp tmp){
		//return 4*(stackPtr.get(tmp)-frameSize);
		return 4*(frameSize-stackPtr.get(tmp));
	}
	public int getLocation(int tmp){
		//return 4*(tmp-frameSize);
		return 4*(frameSize-tmp);
	}
	
	
	public String toAsm() {
		StringBuffer ans = new StringBuffer(".text\n"+name.toString() + ":\n");
		/*for (Temp param: argv) 
			ans += param.toString() + ",";*/
		ans.append("\n");
		ans.append("addiu $sp, $sp,"+(-4*frameSize)+"\n");
		ans.append("sw $ra, "+getLocation(argv.length)+"($sp)\n");
		//The following code works for pseudo-register allocation
		/*for(Quad q: body) {
			ans.append(codegen2(q));
		}*/
		for(BasicBlock b:blocks){
			//TODO: live analysis across basic block / exists/ forall
			if(b.instr.peekFirst() instanceof LabelQuad)exitBlocks(ans);
			entryBlocks(ans);
			for(Quad q:b.instr){
				ans.append(codegen1(q));
			}
		}
		ans.append("_"+name.toString()+"_exit:\n");
		ans.append( "lw $ra, "+getLocation(argv.length)+"($sp)\n");
		ans.append( "addiu $sp, $sp,"+ (4*frameSize) +"\n");
		ans.append( "jr $ra\n");
		return ans.toString();
	}
	
	private void entryBlocks(StringBuffer ret) {
		freeRegs=new LinkedList<String>();
		freeRegs.addAll(Arrays.asList(LinearScan.regs));
		tempInReg=new HashMap<Temp, String>();
		reg2temp=new HashMap<String, HashSet<Temp> >();
		tempInMem=new HashMap<Temp, Integer>();
		tempInMem.putAll(stackPtr);
	}
	
	private void exitBlocks(StringBuffer ret) {
		if(tempInReg==null)return;
		Set<Temp> liveTemps=tempInReg.keySet(); //TODO:live analysis
		if(liveTemps==null)return;
		//HashSet<String> regs2save=new HashSet<String>();
		for(Temp t:liveTemps){
			if(tempInMem.get(t)!=null)continue;
			//regs2save.add(tempInReg.get(t));
			ret.append("sw "+tempInReg.get(t)+", "+getLocation(t)+"($sp)\n");
		}
	}

	
	private StringBuffer codegen1(Quad q) {
		StringBuffer ret=new StringBuffer();
		if(q instanceof BinOp){
			BinaryOp op = ((BinOp)q).op; 
			Oprand left=((BinOp)q).left,right=((BinOp)q).right,dst=((BinOp)q).dst;
			String opcode=((BinOp)q).getAsm(op);
			String [] regs = getReg(ret,op,dst,left,right,((BinOp)q).canFoldByShift());
			String dstR=regs[0],leftR=regs[1],rightR=regs[2];
			
			ret.append(opcode + " "+dstR+" , "+leftR+", "+rightR+"\n");
			tempInMem.remove(((TempOprand)dst).temp);
			return ret;
		}else if(q instanceof Branch){
			BinaryOp op = ((Branch)q).op; 
			Oprand left=((Branch)q).left,right=((Branch)q).right;
			String opcode=((Branch)q).getAsm();
			String [] regs = getReg(ret,op,null,left,right,false);
			String leftR=regs[1],rightR=regs[2];
			Label label=((Branch)q).label;
			exitBlocks(ret);
			ret.append( opcode + " "+leftR+","+rightR+","+label.toString()+"\n");		
			return ret;
		}else if(q instanceof Call){
			for(int i=4;i<((Call)q).params.length;i++){
				Temp t = ((Call)q).params[i];
				String tReg=tempInReg.get(t);
				if(tReg==null){
					ret.append("lw $k0, " + getLocation(t) + "($sp)\n");
					tReg="$k0";
				}
				ret.append("sw "+tReg+", " + (-4*i)+ "($sp)\n");
			}
			for(int i=0;i<min(((Call)q).params.length,4);i++){
				Temp t = ((Call)q).params[i];
				String tReg=tempInReg.get(t);
				if(tReg==null){
					tReg="$a"+i;
					HashSet<Temp> ts=reg2temp.get(tReg);
					if(ts!=null){
						for(Temp tt:ts){
							if(tempInMem.get(tt)==null){//TODO: && t isn't dead
								ret.append("sw "+tReg+","+getLocation(tt)+"($sp)\n");
								tempInMem.put(tt, getLocation(tt));
							}
							tempInReg.remove(tt);
						}
					}
					reg2temp.remove(tReg);
					ret.append("lw "+tReg+", " + getLocation(t) + "($sp)\n");
				}//TODO:also move to $ai
				ret.append("sw "+tReg+", " + (-4*i)+ "($sp)\n");
				/*String curReg="$a"+i;
				tempInReg.put(t, curReg);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t);
				reg2temp.put(curReg, tmp);*/
			}
			exitBlocks(ret);
			ret.append( q.toAsm()+"\n");
			Temp t=((Call)q).result;
			String tReg=null;//tempInReg.get(t);
			if(tReg==null){
				ret.append("sw $v0, " + getLocation(t) + "($sp)\n");
				tempInMem.put(t, getLocation(t));
				tReg=tempInReg.get(t);
				if(tReg!=null){
					reg2temp.get(tReg).remove(t);
				}
				tempInReg.remove(t);
			}else{//TODO:use getRegHelper2 to pick register
				ret.append("move "+tReg+",$v0\n");
			}
			return ret;
		}else if(q instanceof Jump){
			exitBlocks(ret);
			ret.append( q.toAsm()+"\n");		
			return ret;
		}else if(q instanceof LabelQuad){
			ret.append( q.toAsm()+"\n");
			return ret;
		}else if(q instanceof Move){//TODO:
			Oprand src=((Move)q).src,dst=((Move)q).dst;
			String [] regs=getReg2(ret,dst,src,false);
			String dstReg=regs[0],srcReg=regs[1];
			return ret;
		}else if(q instanceof Return){
			Oprand rv=((Return)q).value;
			if(rv instanceof Const){
				ret.append("addiu $v0, $zero," +((Const)((Return)q).value).value + "\n");
			} else if(rv instanceof TempOprand){
				Temp t=((TempOprand)rv).temp;
				String tReg=tempInReg.get(t);
				if(tReg==null){
					ret.append("lw $v0, " + getLocation(((TempOprand)((Return)q).value).temp) + "($sp)\n");
				}else
					ret.append("move $v0,"+tReg+"\n");
			} else if(rv instanceof Mem){
				Temp t=((Mem)rv).base;
				String tReg=tempInReg.get(t);
				if(tReg==null){
					ret.append("lw $v0, " + getLocation(((Mem)rv).base) + "($sp)\n");
					ret.append("lw $v0, " + ((Mem)rv).offset + "($v0)\n");
				}else{
					ret.append("lw $v0, " + ((Mem)rv).offset + "("+tReg+")\n");
				}
			} else error("2012!");
			//exitBlocks(ret);
			ret.append("j _"+name.toString()+"_exit\n");
			ret.append( q.toAsm()+"\n");
			return ret;
		}else if(q instanceof UnaryOp){//TODO:
			String [] regs=new String[2];
			Oprand src=((UnaryOp)q).src,dst=((UnaryOp)q).dst;
			regs=getReg2(ret,dst,src,true);
			ret.append( q.toAsm()+"\n");
			return ret;
		}else {
			error("2012!");
		}
		return ret;
	}
	

	private String[] getReg2(StringBuffer out, Oprand dst, Oprand src,boolean isUnaryOp) {
		String [] ret=new String[2];
		Temp t0=null,t1=null;

		if (dst instanceof Mem){//always !isUnaryOp
			t0=((Mem)dst).base;
			ret[0]=tempInReg.get(t0);
			if(ret[0]!=null&&reg2temp.get(ret[0]).size()>1){//ok?
				if(tempInMem.get(t0)==null){
					out.append("sw "+ret[0]+","+getLocation(t0)+"($sp)\n");
					tempInMem.put(t0,getLocation(t0));
				}
				reg2temp.get(ret[0]).remove(t0);
				ret[0]=null;
			}
			ret[0]=getRegHelper(out,null,ret[0],ret[1],t0,dst,src);
			if(src instanceof TempOprand){
				if(!isUnaryOp){
					t1=((TempOprand)src).temp;
					ret[1]=tempInReg.get(t1);
					ret[1]=getRegHelper(out,null,ret[1],ret[0],t1,src,dst);
					tempInReg.put(t0, ret[0]);
					reg2temp.get(ret[0]).add(t0);
					tempInReg.put(t1, ret[1]);
					reg2temp.get(ret[1]).add(t1);
					out.append("sw "+ret[1]+","+((Mem)dst).offset+"("+ret[0]+")\n");
				}
				else  error("meow");
			}else if(src instanceof Const){
				int value=((Const)src).value;
				
				tempInReg.put(t0, ret[0]);
				if(!isUnaryOp){
					if(value!=0){
						out.append("addiu $k0,$zero, " + ((Const)src).value + "\n");
						ret[1]="$k0";
					}else ret[1]="$zero";
					out.append("sw "+ret[1]+","+((Mem)dst).offset+"("+ret[0]+")\n");
				}else{
					//TODO:
				}
			}else error("meow...");
			
		} else {
			t0=((TempOprand)dst).temp;
			ret[0]=tempInReg.get(t0);
			if(ret[0]!=null&&reg2temp.get(ret[0]).size()>1){//ok?
				if(tempInMem.get(t0)==null){
					out.append("sw "+ret[0]+","+getLocation(t0)+"($sp)\n");
					tempInMem.put(t0,getLocation(t0));
				}
				reg2temp.get(ret[0]).remove(t0);
				ret[0]=null;
			}
			if(src instanceof TempOprand){
				t1=((TempOprand)src).temp;
				ret[1]=tempInReg.get(t1);
				if(ret[1]!=null&&!isUnaryOp){//src in Reg
					if(ret[0]!=null){//dst in Reg && ok
						reg2temp.get(ret[0]).remove(t0);
					}
					tempInMem.remove(t0);
					tempInReg.put(t0, ret[1]);
					reg2temp.get(ret[1]).add(t0);
					ret[0]=ret[1];
					return ret;
				}
				if(ret[1]!=null&&isUnaryOp){//src in Reg
					//TODO:
					return ret;
				}
				if(ret[0]!=null){//dst in Reg && ok
					out.append("lw "+ ret[0] +","+getLocation(t1)+"($sp)\n");
					if(!isUnaryOp){
						tempInMem.remove(t0);
						tempInReg.put(t1, ret[0]);
						reg2temp.get(ret[0]).add(t1);
						ret[1]=ret[0];
					}else {
						//TODO:
					}
				}else	{
					ret[0]=getRegHelper2(out,ret[0],t0);
					out.append("lw "+ ret[0] +","+getLocation(t1)+"($sp)\n");
					if(!isUnaryOp){
						tempInMem.remove(t0);
						tempInReg.put(t1, ret[0]);
						tempInReg.put(t0, ret[0]);
						reg2temp.get(ret[0]).add(t1);
						ret[1]=ret[0];
					}else {
						//TODO:
					}
				}
			}else if(src instanceof Mem){
				ret[0]=getRegHelper2(out,ret[0],t0);
				t1=((Mem)src).base;
				int length=((Mem)src).length;
				ret[1]=tempInReg.get(t1);
				
				
				String opcode=length==4?"lw ":"lb ";
				ret[1]=getRegHelper(out,dst,ret[1],null,t1,src,null);
				out.append(opcode+ret[0]+","+((Mem)src).offset + "("+ret[1]+")\n");
				if(!isUnaryOp){
					tempInMem.remove(t0);
					tempInReg.put(t0, ret[0]);
					reg2temp.get(ret[0]).add(t0);
				}else{
					//TODO:
				}
			}else if(src instanceof Const){
				ret[0]=getRegHelper2(out,ret[0],t0);
				int value=((Const)src).value;
				ret[1]=String.valueOf(value);
				tempInMem.remove(t0);
				tempInReg.put(t0, ret[0]);
				if(!isUnaryOp){
					out.append("addiu "+ret[0]+",$zero, " + ((Const)src).value + "\n");
				}else{
					//TODO:
				}
			}else if(src instanceof LabelAddress){
				ret[0]=getRegHelper2(out,ret[0],t0);
				tempInMem.remove(t0);
				tempInReg.put(t0, ret[0]);
				if(!isUnaryOp){
					out.append("la "+ret[0]+", " + ((LabelAddress)src).label + "\n");
				}else{
					error("meow!!!");
				}
			}
			//ans+= "sw "+srcReg+", " + getLocation(((TempOprand)((Move)q).dst).temp) + "($sp)\n";
		}
		return ret;
	}

	private int min(int a, int b) {
		return a<b?a:b;
	}

	//BinOp or Move: no a[i]=a[j] or a[i]=x+y form.
	//if src is not const then it's loaded otherwise it's not.
	//this works only for BinOp and Branch
	private String[] getReg(StringBuffer out,BinaryOp op,Oprand dst,Oprand src1,Oprand src2,boolean canFoldByShift) {
		//TODO:if( src instanceof Const)check whether it's a fixed constant and whether k0/k1 always contains that constant 
		
		String [] ret= new String[3];
		Temp t1=null,t2=null,t0=null;
		if(src1 instanceof TempOprand){
			t1=((TempOprand)src1).temp;
			ret[1]=tempInReg.get(t1);
		}else if(src1 instanceof Mem){
			t1=((Mem)src1).base;
			ret[1]=tempInReg.get(t1);
		}else if(src1 instanceof Const){
			if(((Const)src1).value!=0)ret[1]="$k0";
			else ret[1]="$zero";
		}else
			error("2012!getReg!src1");
		ret[1]=getRegHelper(out,dst,ret[1],ret[2],t1,src1,src2);
		
		if(src2!=null){//FIXME:what's this?
			if(src2 instanceof TempOprand){
				t2=((TempOprand)src2).temp;
				ret[2]=tempInReg.get(t2);
			}else if(src2 instanceof Mem){
				t2=((Mem)src2).base;
				ret[2]=tempInReg.get(t2);
			}else if(src2 instanceof Const){
				if(((Const)src2).value!=0)ret[2]="$k1";
				else ret[2]="$zero";
			}else 
				error("2012!getReg!src2");
			ret[2]=getRegHelper(out,dst,ret[2],ret[1],t2,src2,src1);
		}
		
		if(src1 instanceof Const&&op==BinaryOp.PLUS){
			if(src2 instanceof Const)ret[1]=Integer.toString(((Const)src1).value);
			else {
				ret[1]=ret[2];
				ret[2]=Integer.toString(((Const)src1).value);
			}
		}
		if(src2 instanceof Const&&op==BinaryOp.PLUS){
			ret[2]=Integer.toString(((Const)src2).value);
		}
		if(canFoldByShift)
			ret[2]=Integer.toString(BinOp.log2(((Const)src2).value));
		
		if(ret[1].length()>=2&&ret[1].substring(0, 2).equals("$k")){
			out.append("addiu "+ret[1]+",$zero, " + ((Const)src1).value + "\n");
		}
		if(ret[2].length()>=2&&ret[2].substring(0, 2).equals("$k")){
			out.append("addiu "+ret[2]+",$zero, " + ((Const)src2).value + "\n");
		}

		if(dst!=null){
			if(dst instanceof TempOprand){
				t0=((TempOprand)dst).temp;
				ret[0]=tempInReg.get(t0);
				if(ret[0]!=null&&reg2temp.get(ret[0]).size()>1){//ok?
					if(tempInMem.get(t0)==null){
						out.append("sw "+ret[0]+","+getLocation(t0)+"($sp)\n");
						tempInMem.put(t0,getLocation(t0));
					}
					reg2temp.get(ret[0]).remove(t0);
					ret[0]=null;
				}
			}else
				error("2012!getReg!dst");
			//TODO:favor choosing src1 or src2 if they're dead.
			ret[0]=getRegHelper2(out,ret[0],t0);
		}
		

		if(src1 instanceof Mem){
			out.append("lw $k0, "+ ((Mem)src1).offset+ "("+ret[1]+")\n");
			ret[1]="$k0";
		}
		if(src2 instanceof Mem){
			out.append("lw $k1, "+ ((Mem)src2).offset+ "("+ret[2]+")\n");
			ret[2]="$k1";
		}
		
		return ret;
	}
	
	//this works particularly for selecting dst
	private String getRegHelper2(StringBuffer out,String ret1,Temp t1) {
		if(ret1==null){
			ret1=freeRegs.poll();
			if(ret1==null){
				//TODO: use linear scan instead of store count?
				int storeCount=32;
				String pickedReg="";
				String stInstr="";
				for(String v : reg2temp.keySet()){
					HashSet<Temp> vs=reg2temp.get(v);
					int curSTcnt=0;
					String curSTInstr="";
					if(vs!=null){
						for(Temp rt:vs){
							boolean ok=tempInMem.get(rt)!=null
									||rt.equals(t1)
									;//TODO:and rt isn't dead
							if(!ok){
								curSTcnt++;
								curSTInstr+="sw "+ret1+", "+ getLocation(rt)+ "($sp)\n";
							}
						}
					}
					if(curSTcnt<storeCount){
						storeCount=curSTcnt;
						pickedReg=v;
						stInstr=curSTInstr;
						//if(curSTcnt==0)break;
					}
				}
				out.append(stInstr);
				HashSet<Temp> vs=reg2temp.get(pickedReg);
				if(vs!=null){
					for(Temp rt:vs){
						tempInMem.put(rt, getLocation(rt));
						tempInReg.remove(rt);
					}
					
				}
				ret1=pickedReg;
				tempInReg.put(t1, ret1);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t1);
				reg2temp.put(ret1, tmp);
			}else{
				tempInReg.put(t1, ret1);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t1);
				reg2temp.put(ret1, tmp);
			}
		}
		
		return ret1;
	}
	
	//this works particularly for BinOp and Branch
	private String getRegHelper(StringBuffer out,Oprand dst,String ret1,String ret2,Temp t1,Oprand src1,Oprand src2) {
		if(ret1==null){
			ret1=freeRegs.poll();
			if(ret1==null){
				//TODO: use linear scan instead of store count?
				int storeCount=32;
				String pickedReg="";
				String stInstr="";
				for(String v : reg2temp.keySet()){
					HashSet<Temp> vs=reg2temp.get(v);
					int curSTcnt=0;
					String curSTInstr="";
					if(ret2!=null&&ret2.equals(v))continue;
					if(vs!=null){
						for(Temp rt:vs){
							Temp dstT = null,src2T=null;
							if(dst instanceof TempOprand){
								dstT=((TempOprand)dst).temp;
							}
							if(src2 instanceof TempOprand){
								src2T=((TempOprand)src2).temp;
							}else if(src2 instanceof Mem){
								src2T=((Mem)src2).base;
							}
							boolean ok=tempInMem.get(rt)!=null
									||(dstT!=null&&rt==dstT&&(src2T==null||rt!=src2T))
									;//TODO:and rt isn't dead
							if(!ok){
								curSTcnt++;
								curSTInstr+="sw "+ret1+", "+ getLocation(rt)+ "($sp)\n";
							}
						}
					}
					if(curSTcnt<storeCount){
						storeCount=curSTcnt;
						pickedReg=v;
						stInstr=curSTInstr;
						//if(curSTcnt==0)break;
					}
				}
				out.append(stInstr);
				HashSet<Temp> vs=reg2temp.get(pickedReg);
				if(vs!=null){
					for(Temp rt:vs){
						tempInMem.put(rt, getLocation(rt));
						tempInReg.remove(rt);
					}
				}
				ret1=pickedReg;
				tempInReg.put(t1, ret1);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t1);
				reg2temp.put(ret1, tmp);
				out.append("lw "+ret1+", "+ getLocation(t1)+ "($sp)\n");
			}else{
				tempInReg.put(t1, ret1);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t1);
				reg2temp.put(ret1, tmp);
				out.append("lw "+ret1+", "+ getLocation(t1)+ "($sp)\n");
			}
		}
		return ret1;
	}
	
	
	
	
	//The following code works for pseudo-register allocation
	public String codegen_pseudo(Quad q) {
			String ans="";
			if(q instanceof BinOp){
				if(((BinOp)q).left instanceof TempOprand){
					ans+= "lw $t0, " + getLocation(((TempOprand)((BinOp)q).left).temp) + "($sp)\n";
				} else if(((BinOp)q).left instanceof Const){
					if(((BinOp)q).op!=BinaryOp.PLUS)
						ans+= "addiu $t0,$zero, " + ((Const)((BinOp)q).left).value + "\n";
				} else if(((BinOp)q).left instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((BinOp)q).left).base) + "($sp)\n";
					ans+= "lw $t0, " + ((Mem)((BinOp)q).left).offset + "($t0)\n";
				} else error("2012binop"+((BinOp)q).left.getClass());
				
				if(((BinOp)q).right instanceof TempOprand){
					ans+= "lw $t1, " + getLocation(((TempOprand)((BinOp)q).right).temp) + "($sp)\n";
				} else if(((BinOp)q).right instanceof Const){
					if(((BinOp)q).op!=BinaryOp.PLUS&&!((BinOp)q).canFoldByShift())
						ans+= "addiu $t1,$zero, " + ((Const)((BinOp)q).right).value + "\n";
				} else if(((BinOp)q).right instanceof Mem){
					ans+= "lw $t1, " + getLocation(((Mem)((BinOp)q).right).base) + "($sp)\n";
					ans+= "lw $t1, " + ((Mem)((BinOp)q).right).offset + "($t1)\n";
				} else error("2012binop"+((BinOp)q).right.getClass());
			}else if (q instanceof Branch){
				if(((Branch)q).left instanceof TempOprand)
					ans+= "lw $t0, " + getLocation(((TempOprand)((Branch)q).left).temp) + "($sp)\n";
				else if(((Branch)q).left instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((Branch)q).left).base) + "($sp)\n";
					ans+= "lw $t0, " + ((Mem)((Branch)q).left).offset + "($t0)\n";
				}else if(((Branch)q).left instanceof Const){
					if(((Const)((Branch)q).left).value!=0)
						ans+= "addiu $t0,$zero, " + ((Const)((Branch)q).left).value + "\n";
				} else error("2012branch");
				
				if(((Branch)q).right instanceof TempOprand)
					ans+= "lw $t1, " + getLocation(((TempOprand)((Branch)q).right).temp) + "($sp)\n";
				else if(((Branch)q).right instanceof Mem){
					ans+= "lw $t1, " + getLocation(((Mem)((Branch)q).right).base) + "($sp)\n";
					ans+= "lw $t1, " + ((Mem)((Branch)q).right).offset + "($t1)\n";
				}else if(((Branch)q).right instanceof Const){
					if(((Const)((Branch)q).right).value!=0)
						ans+= "addiu $t1,$zero, " + ((Const)((Branch)q).right).value + "\n";
				}else error("2012branch");
			}else if (q instanceof Call){
				for(int i=0;i<((Call)q).params.length;i++){
					Temp t = ((Call)q).params[i];
					ans += "lw $t0, " + getLocation(t) + "($sp)\n";
					ans += "sw $t0, " + (-4*i)+ "($sp)\n";
				}
			}else if (q instanceof Jump){
				
			}else if (q instanceof LabelQuad){
			
			}else if (q instanceof Move){
				if(((Move)q).src instanceof Const){
					if(((Const)((Move)q).src).value!=0)ans+= "addiu $t1,$zero, " + ((Const)((Move)q).src).value + "\n";
				} else if (((Move)q).src instanceof LabelAddress){
					ans+= "la $t1, " + ((LabelAddress)((Move)q).src).label + "\n";
				}else if (((Move)q).src instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((Move)q).src).base) + "($sp)\n";
					if(((Mem)((Move)q).src).length==4)ans+= "lw $t1, " + ((Mem)((Move)q).src).offset + "($t0)\n";
					else if(((Mem)((Move)q).src).length==1)ans+= "lb $t1, " + ((Mem)((Move)q).src).offset + "($t0)\n";
				} else {
					ans+= "lw $t1, " + getLocation(((TempOprand)((Move)q).src).temp) + "($sp)\n";
				}
			}else if (q instanceof Return){
				if(((Return)q).value instanceof Const){
					ans+= "addiu $v0, $zero," +((Const)((Return)q).value).value + "\n";
				} else if(((Return)q).value instanceof TempOprand){
					ans+= "lw $v0, " + getLocation(((TempOprand)((Return)q).value).temp) + "($sp)\n";
				} else if(((Return)q).value instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((Return)q).value).base) + "($sp)\n";
					ans+= "lw $v0, " + ((Mem)((Return)q).value).offset + "($t0)\n";
				} else error("2012!");
				ans+= "j _"+name.toString()+"_exit\n";
			}else if (q instanceof UnaryOp){
				if(((UnaryOp)q).src instanceof TempOprand)
					ans+= "lw $t1, " + getLocation(((TempOprand)((UnaryOp)q).src).temp) + "($sp)\n";
				else if(((UnaryOp)q).src instanceof Const){
					ans+= "addiu $t1,$zero, " + ((Const)((UnaryOp)q).src).value + "\n";
				}else if(((UnaryOp)q).src instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((UnaryOp)q).src).base) + "($sp)\n";
					ans+= "lw $t1, " + ((Mem)((UnaryOp)q).src).offset + "($t0)\n";
				}else error("2012!");
			}else {
				error("2012!");
			}
			
			ans += q.toAsm() + "\n";
			
			if(q instanceof BinOp){
				if(((BinOp)q).dst instanceof TempOprand){
					ans+= "sw $t2, " + getLocation(((TempOprand)((BinOp)q).dst).temp) + "($sp)\n";
				}/*else if(((BinOp)q).dst instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((BinOp)q).dst).base) + "($sp)\n";
					ans+= "sw $t2, " + ((Mem)((BinOp)q).dst).offset + "($t0)\n";
				}*/
				else error("2012BinOp.dst");
			}else if(q instanceof Branch){

			}else if(q instanceof Call){
				ans += "sw $v0, " + getLocation(((Call)q).result) + "($sp)\n";
			}else if(q instanceof Jump){

			}else if(q instanceof LabelQuad){
				
			}else if(q instanceof Move){
				String dstReg="$t1";
				if(((Move)q).src instanceof Const&&((Const)((Move)q).src).value==0){
					dstReg="$zero";					
				}
				if (((Move)q).dst instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((Move)q).dst).base) + "($sp)\n";
					ans+= "sw "+dstReg+", " + ((Mem)((Move)q).dst).offset + "($t0)\n";
				} else {
					ans+= "sw "+dstReg+", " + getLocation(((TempOprand)((Move)q).dst).temp) + "($sp)\n";
				}
			}else if(q instanceof Return){

			}else if(q instanceof UnaryOp){
				if(((UnaryOp)q).dst instanceof TempOprand)
					ans+= "sw $t1, " + getLocation(((TempOprand)((UnaryOp)q).dst).temp) + "($sp)\n";
				else if(((UnaryOp)q).src instanceof Mem){
					ans+= "lw $t0, " + getLocation(((Mem)((UnaryOp)q).dst).base) + "($sp)\n";
					ans+= "sw $t1, " + ((Mem)((UnaryOp)q).dst).offset + "($t0)\n";
				}else error("2012!");
			}else {
				error("2012!");
			}
			return ans;
		}

		

		private void error(String string) {
			throw new SException(string);
		}
}
