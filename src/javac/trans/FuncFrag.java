/*
 * Stack Frame Convention:
 * argv.length : reserved for return address.
 * argv.length+1 : reserved for ?.
 */
package javac.trans;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javac.semantic.SException;

import javac.absyn.BinaryOp;
import javac.block.BasicBlock;
import javac.block.LiveAnalysis;
import javac.linearScan.Interval;
import javac.linearScan.LinearScan;
import javac.quad.*;

public class FuncFrag extends Frags {

	public Label name;
	public LinkedList<Quad> body;
	public LinkedList<BasicBlock> blocks;
	public Temp [] argv;
	public HashMap<Temp, Integer> stackPtr;
	public HashMap<Temp,String> tempInReg;
	public HashMap<Temp,Integer> tempInMem;
	public HashMap<String,HashSet<Temp> > reg2temp;//register descriptor
	public LinkedList<String> freeRegs;
	public static Integer [] canInline0I={1,4,5,9,11,8,12};
	public static String [] canInline0S= {"printInt","printString","readInt","_malloc","printChar","readString","readChar"};
	public static String [] nativeCallsInRegs={"printInt","printString","readInt","_malloc","printChar","readString","readChar","_strlen","_strcmp","_strcat","fillIntArray","_intToString","substring","printLine"};
	public static String [] nativeCallRegs={"$v0"};//k0,k1,v0,"$v1","$ra"
	public static String [] noSideEffectCalls={"_malloc","_strlen","_strcmp","_strcat","_intToString","substring","_charToString","ord","chr"};
	public static HashMap<String,Integer> canInline0;
	public static int v0syscall=-1;
	public static HashSet<String> nativeCallsInReg;
	public static HashSet<String> noSideEffectCall;
	public boolean curLiveEnabled=false;
	
	public static int totalRegSize=LinearScan.regs.length;
	public int frameSize;
	public int hasCall=0;
	public int hasSysCall=0;
	public boolean hasSideEffect=false;
	public static StringBuffer ans;
	
	public FuncFrag() {
	}

	public FuncFrag(Label name) {
		this.name=name;
	}
	
	public static void init(){
		canInline0=new HashMap<String,Integer>();
		nativeCallsInReg=new HashSet<String>(Arrays.asList(nativeCallsInRegs));
		for(int i=0;i<canInline0S.length;i++){
			canInline0.put(canInline0S[i], canInline0I[i]);
		}
	}
	public String toString() {
		String ans = name.toString() + ":\n";
		for (Temp param: argv) 
			ans += param.toString() + ",";
		ans += "\n";
		/*for(Quad q: body) {
			ans += q.toString() + "\n";
		}*/
		for(BasicBlock b:blocks){
			for(Quad q:b.instr){
				if(q.killed)ans+=q.toString() +"###killed\n";
				else ans+=q.toString() + "\n";
			}
			ans+=("###exiting block##1:"+b.toString()+"\n");
			for(Temp t:b.liveOut){
				ans+=("###exiting block##OUT:"+t.toString()+"\n");
			}
			ans+=("###exiting block##2\n");
		}
		return ans;
	}
	public int getLocation(Temp tmp){
		if(!stackPtr.containsKey(tmp))return -2012;
		if(hasCall>0)return 4*(frameSize-stackPtr.get(tmp));
		else return 4*(-stackPtr.get(tmp));
	}
	public int getLocation(int tmp){
		if(hasCall>0)return 4*(frameSize-tmp);
		else return 4*(-tmp);
	}
	
	public static boolean isSideEffectCall(Call q){
		noSideEffectCall=new HashSet<String>(Arrays.asList(noSideEffectCalls));
		boolean ret=!noSideEffectCall.contains(q.function.toString());
		ret=ret||q.hasArrayOrRecord;
		return ret;
	}
	//TODO:maybe more careful analysis of live range.
	public boolean hasNextUseAfter(BasicBlock b,Temp t,int insno){
		if(t==null||t.isOnceTemp){
			if(t==null)return false;
			if(t.liveEnd>insno)return true;
			else return false;
		}
		if(LiveAnalysis.semiEnabled||LiveAnalysis.enabled||curLiveEnabled){
			if(LiveAnalysis.enabled||curLiveEnabled)
				return b.isLiveOut(t)||(b.liveRange.containsKey(t)?b.liveRange.get(t).endpoint>insno:false);
			return b.liveRange.containsKey(t)?b.liveRange.get(t).endpoint>insno:true;
		}else return true;
	}
	public String toAsm() {
		if(Trans.killedFunc.contains(name))return "\n";
		ans = new StringBuffer(".text\n"+(name.toString().equals("main")?"":"_")+name.toString() + ":\n");
		ans.append("\n");
		if(hasCall>0)
			ans.append("addu $sp, $sp,"+(-4*frameSize)+"\n");
		if(!canFullReg1()||hasCall>0)
			ans.append("sw $ra, "+getLocation(argv.length)+"($sp)\n");
		BasicBlock lastB=null;
		entryBlocks(true, -1, blocks.peekFirst(), false, null);
		/*for(int i=0;i<min(4,argv.length);i++){
			freeRegs.remove(argv[i]);
			tempInReg.put(argv[i], "$a"+i);
			HashSet<Temp> tmp=new HashSet<Temp>();
			tmp.add(argv[i]);
			reg2temp.put("$a"+i,tmp);
			tempInMem.remove(tmp);
		}*/
		for(BasicBlock b:blocks){
			//TODO: live analysis across basic block / exists/ forall
			if(lastB!=null&&b.instr.peekFirst() instanceof LabelQuad)exitBlocks(lastB,false);
			/*if(lastB!=null&&lastB.instr.peekLast() instanceof Call)
				entryBlocks(ans, true, b.instr.peekFirst().instructionNo-1, b, true, (Call)lastB.instr.peekLast());*/
			if(lastB!=null&&!(lastB.instr.peekLast() instanceof Call)){
				entryBlocks(false, b.instr.peekFirst().instructionNo-1, b, false, null);
			}
			HashMap<Interval,String> regMaps=null;
			if(LiveAnalysis.enabled||curLiveEnabled)regMaps=LinearScan.getRegMap(b);
			for(Quad q:b.instr){
				if(q.killed)continue;
				codegen1(b,q);
			}
			lastB=b;
		}
		ans.append("_"+name.toString()+"_exit:\n");
		if(!canFullReg1()||hasCall>0)
			ans.append( "lw $ra, "+getLocation(argv.length)+"($sp)\n");
		if(hasCall>0)
			ans.append( "addu $sp, $sp,"+ (4*frameSize) +"\n");
		ans.append( "jr $ra\n");
		return ans.toString();
	}
	
	private void entryBlocks(boolean isFuncEnter, int insno, BasicBlock b, boolean isFuncReturn, Call lastCall) {
		if(isFuncEnter&&canFullReg()&&isFuncReturn){
			//restores all temps needed
			HashSet<String> restoredRegs=new HashSet<String>();
			
			if(isInline0(lastCall)&&argv.length>0){
				String curReg="$a0";
				HashSet<Temp> ts= reg2temp.get(curReg);
				Temp tt=null;
				if(ts!=null){
					for(Temp t:ts){
						if(!hasNextUseAfter(b,t,insno))continue;
						tt=t;
						break;
					}
				}
				if(tt!=null)ans.append("lw "+curReg+","+getLocation(tt)+"($sp)\n");
				return ;
			}
			if(isNativeCallsInReg(lastCall)){
				for(int i=0;i<min(4,lastCall.params.length);i++){
					String curReg="$a"+i;
					HashSet<Temp> ts= reg2temp.get(curReg);
					Temp tt=null;
					if(ts!=null){
						for(Temp t:ts){
							if(!hasNextUseAfter(b,t,insno))continue;
							tt=t;
							break;
						}
					}
					if(tt!=null)ans.append("lw "+curReg+","+getLocation(tt)+"($sp)\n");
				}
				return ;
			}
			Set<Temp> liveTemps=new HashSet<Temp>(tempInReg.keySet());
			for(Temp t:liveTemps){
				if(!hasNextUseAfter(b,t,insno))continue;
				String curReg=tempInReg.get(t);
				if(restoredRegs.contains(curReg))continue;
				if(tempInMem.get(t)!=null&&curReg!=null){
					ans.append("lw "+curReg+","+getLocation(t)+"($sp)\n");
					restoredRegs.add(curReg);
				}				
			}
			return ;
		}
		if(canFullReg()&&!isFuncEnter){
			/*for(Temp t:b.liveIn){
				tempInMem.remove(t);
			}*/
			return;
			
		}
		
		if(lastCall!=null&&isNativeCallsInReg(lastCall))return;
		v0syscall=-1;
		freeRegs=new LinkedList<String>();
		freeRegs.addAll(Arrays.asList(LinearScan.regs));
		tempInReg=new HashMap<Temp, String>();
		reg2temp=new HashMap<String, HashSet<Temp> >();
		tempInMem=new HashMap<Temp, Integer>();
		tempInMem.putAll(stackPtr);
		if(canFullReg()&&isFuncEnter){
			for(int i=0;i<argv.length;i++){
				Temp t0=argv[i];
				if(!hasNextUseAfter(b,t0,insno))continue;
				String reg=dstInRegAndOk(t0,false);
				reg=dstInRegAndOk(t0,false);
				reg=getRegHelper2(reg,t0,b,insno,null);
				tempInReg.put(t0, reg);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t0);
				reg2temp.put(reg,tmp);
				//tempInMem.remove(tmp);
				ans.append("lw "+ reg+","+getLocation(t0)+"($sp)\n");
			}
		}
	}

	private void entryBlocksSysCall(boolean isFuncEnter, int insno, BasicBlock b, boolean isFuncReturn, SysCall lastCall) {
		if(isFuncEnter&&canFullReg()&&isFuncReturn){
			//restores all temps needed
			HashSet<String> restoredRegs=new HashSet<String>();
			if(isInline0(lastCall)&&argv.length>0){
				String curReg="$a0";
				HashSet<Temp> ts= reg2temp.get(curReg);
				Temp tt=null;
				if(ts!=null){
					for(Temp t:ts){
						if(!hasNextUseAfter(b,t,insno))continue;
						tt=t;
						break;
					}
				}
				if(tt!=null)ans.append("lw "+curReg+","+getLocation(tt)+"($sp)\n");
				return ;
			}
			for(int i=0;i<min(4,lastCall.params.length);i++){
				String curReg="$a"+i;
				HashSet<Temp> ts= reg2temp.get(curReg);
				Temp tt=null;
				if(ts!=null){
					for(Temp t:ts){
						if(!hasNextUseAfter(b,t,insno))continue;
						tt=t;
						break;
					}
				}
				if(tt!=null)ans.append("lw "+curReg+","+getLocation(tt)+"($sp)\n");
			}
			return ;
		}
	}
	
	private void exitBlocks(BasicBlock b,boolean isFuncExit) {
		if(tempInReg==null||b==null)return;
		v0syscall=-1;
		if(canFullReg()&&!isFuncExit)return;
		Set<Temp> liveTemps=new HashSet<Temp>(tempInReg.keySet()); 
		if(b.liveOut!=null&&(LiveAnalysis.enabled||curLiveEnabled))liveTemps.retainAll(b.liveOut);

		for(Temp t:liveTemps){
			spill2Mem(t,canFullReg());
		}
	}

	public static boolean isInline0(Call q){//for syscall
		if(canInline0==null)init();
		return canInline0.containsKey(q.function.toString());
	}
	public static boolean isInline0(SysCall q){//for syscall
		if(canInline0==null)init();
		return canInline0.containsKey(q.function.toString());
	}

	public static boolean isNativeCallsInReg(Call q) {
		if(nativeCallsInReg==null)init();
		return nativeCallsInReg.contains(q.function.toString());
		
	}
	public static boolean isNativeCallsInReg(String q) {
		if(nativeCallsInReg==null)init();
		return nativeCallsInReg.contains(q);
		
	}
	
	private void codegen1(BasicBlock b, Quad q) {
		if(q instanceof BinOp){
			BinaryOp op = ((BinOp)q).op; 
			Oprand left=((BinOp)q).left,right=((BinOp)q).right,dst=((BinOp)q).dst;
			String opcode=((BinOp)q).getAsm(op);
			String [] regs = getReg(op,dst,left,right,((BinOp)q).canFoldByShift(),b,q.instructionNo);
			String dstR=regs[0],leftR=regs[1],rightR=regs[2];
			if(right instanceof Const&&op==BinaryOp.MINUS){
				opcode="addiu";
			}
			ans.append(opcode + " "+dstR+" , "+leftR+", "+rightR+"\n");
			tempInMem.remove(((TempOprand)dst).temp);
			return;
		}else if(q instanceof Branch){
			BinaryOp op = ((Branch)q).op; 
			Oprand left=((Branch)q).left,right=((Branch)q).right;
			String opcode=((Branch)q).getAsm();
			String [] regs = getReg(op,null,left,right,false,b,q.instructionNo);
			String leftR=regs[1],rightR=regs[2];
			Label label=((Branch)q).label;
			exitBlocks(b,false);
			ans.append( opcode + " "+leftR+","+rightR+","+label.toString()+"\n");		
			return ;
		}else if(q instanceof Call){
			for(int i=4;i<((Call)q).params.length;i++){
				Temp t = ((Call)q).params[i];
				String tReg=tempInReg.get(t);
				if(tReg==null){
					ans.append("lw $k0, " + getLocation(t) + "($sp)\n");
					tReg="$k0";
				}
				ans.append("sw "+tReg+", " + (-4*i)+ "($sp)\n");
			}
			if(isNativeCallsInReg((Call)q)){
				for(int i=0;i<min(((Call)q).params.length,4);i++){
					Temp t = ((Call)q).params[i];
					String tReg=tempInReg.get(t);
					String zReg="$a"+i;
					HashSet<Temp> ts=reg2temp.get(zReg);
					boolean ok=false;
					if(ts!=null){
						for(Temp tt:ts){
							if(tt.equals(t))ok=true;
							if(tempInMem.get(tt)==null&&((isNativeCallsInReg((Call)q)&&hasNextUseAfter(b,tt,q.instructionNo))||b.isLiveOut(tt))){
								ans.append("sw "+zReg+","+getLocation(tt)+"($sp)\n");
								tempInMem.put(tt, getLocation(tt));
							}
							if(!canFullReg())
								tempInReg.remove(tt);
						}
					}
					if(!canFullReg())
						reg2temp.remove(zReg);
					if(tReg==null){
						if(!ok)ans.append("lw "+zReg+", " + getLocation(t) + "($sp)\n");
					}else{ 
						if(!ok)ans.append("move "+zReg+", " + tReg+"\n");
					}
					tReg=zReg;
				}
			}else {
				for(int i=0;i<min(((Call)q).params.length,4);i++){
					Temp t = ((Call)q).params[i];
					String tReg=tempInReg.get(t);
					if(tReg==null){
						ans.append("lw $k0, " + getLocation(t) + "($sp)\n");
						tReg="$k0";
					}
					ans.append("sw "+tReg+", " + (-4*i)+ "($sp)\n");
				}
			}
			
			if(!isNativeCallsInReg((Call)q))
				exitBlocks(b,true);
			else {
				for(String Reg:nativeCallRegs){
					HashSet<Temp> ts=reg2temp.get(Reg);
					if(ts!=null){
						for(Temp tt:ts){
							if(tempInMem.get(tt)==null&&(b.isLiveOut(tt)||hasNextUseAfter(b,tt,q.instructionNo))){// hasNextUse
								ans.append("sw "+Reg+","+getLocation(tt)+"($sp)\n");
								tempInMem.put(tt, getLocation(tt));
							}
							if(!canFullReg())tempInReg.remove(tt);
						}
					}
					if(!canFullReg())reg2temp.remove(Reg);
				}
			}
			
			if(isInline0((Call)q)){
				int newv0syscall=canInline0.get(((Call)q).function.toString());
				if(newv0syscall!=v0syscall||(v0syscall!=1&&v0syscall!=4)){//TODO:consider also other syscall without $v0 being modified
					ans.append("li $v0,"+newv0syscall+"\n");
					v0syscall=newv0syscall;
				}
				ans.append("syscall\n");
			}else {
				ans.append( q.toAsm()+"\n");
			}
			
			Temp t=((Call)q).result;
			String tReg=null;
			if(b.isLiveOut(t)||hasNextUseAfter(b,t,q.instructionNo)){
				if((!((Call)q).killedret||b.isLiveOut(t))&&t!=null)
					ans.append("sw $v0, " + getLocation(t) + "($sp)\n");
			}
			tempInMem.put(t, getLocation(t));
			if(!canFullReg()){
				tReg=tempInReg.get(t);
				if(tReg!=null){
					reg2temp.get(tReg).remove(t);
				}
				tempInReg.remove(t);
			}
			entryBlocks(true, q.instructionNo, b, true, (Call)q);

			return ;
		}else if(q instanceof SysCall){
			for(int i=0;i<min(((SysCall)q).params.length,4);i++){
				Oprand tmp=((SysCall)q).params[i];
				String zReg="$a"+i;
				if(tmp instanceof TempOprand){
					Temp t = ((TempOprand)tmp).temp;
					String tReg=tempInReg.get(t);
					HashSet<Temp> ts=reg2temp.get(zReg);
					boolean ok=false;
					if(ts!=null){
						for(Temp tt:ts){
							if(tt.equals(t))ok=true;
							if(tempInMem.get(tt)==null&&(hasNextUseAfter(b,tt,q.instructionNo)||b.isLiveOut(tt))){
								ans.append("sw "+zReg+","+getLocation(tt)+"($sp)\n");
								tempInMem.put(tt, getLocation(tt));
							}
							if(!canFullReg())
								tempInReg.remove(tt);
						}
					}
					if(!canFullReg())
						reg2temp.remove(zReg);
					if(tReg==null){
						if(!ok)ans.append("lw "+zReg+", " + getLocation(t) + "($sp)\n");
					}else{ 
						if(!ok)ans.append("move "+zReg+", " + tReg+"\n");
					}
					tReg=zReg;
				}else if(tmp instanceof Const){
					int value=((Const)tmp).value;
					if(value<32767&&value>-32768)ans.append("addiu "+zReg+", $zero," +value + "\n");
					else ans.append("li "+zReg+", " +value + "\n");
				}else if(tmp instanceof LabelAddress){
					ans.append("la "+zReg+", "+((LabelAddress)tmp).label.toString()+"\n");
				}else if(tmp instanceof Mem){
					Temp t = ((Mem)tmp).base;
					String tReg=tempInReg.get(t);
					String opcode=((Mem)tmp).length==4?"lw ":"lb";
					if(tReg==null){
						ans.append("lw "+zReg+", "+getLocation(((Mem)tmp).base)+"($sp)\n");
						ans.append(opcode+zReg+", "+((Mem)tmp).offset+"("+zReg+")\n");
					}else {
						ans.append(opcode+zReg+", "+((Mem)tmp).offset+"("+tReg+")\n");
					}
				}else error("meow........");
				
			}
			for(String Reg:nativeCallRegs){
				HashSet<Temp> ts=reg2temp.get(Reg);
				if(ts!=null){
					for(Temp tt:ts){
						if(tempInMem.get(tt)==null&&(b.isLiveOut(tt)||hasNextUseAfter(b,tt,q.instructionNo))){// hasNextUse
							ans.append("sw "+Reg+","+getLocation(tt)+"($sp)\n");
							tempInMem.put(tt, getLocation(tt));
						}
						if(!canFullReg())tempInReg.remove(tt);
					}
				}
				if(!canFullReg())reg2temp.remove(Reg);
			}
			if(isInline0((SysCall)q)){
				int newv0syscall=canInline0.get(((SysCall)q).function.toString());
				if(newv0syscall!=v0syscall||(v0syscall!=1&&v0syscall!=4)){//TODO:consider also other syscall without $v0 being modified
					ans.append("li $v0,"+newv0syscall+"\n");
					v0syscall=newv0syscall;
				}
				ans.append("syscall\n");
			}else {
				ans.append( q.toAsm()+"\n");
			}
			
			Temp t=((SysCall)q).result;
			String tReg=null;
			if(b.isLiveOut(t)||hasNextUseAfter(b,t,q.instructionNo)){
				if((!((SysCall)q).killedret||b.isLiveOut(t))&&t!=null)
					ans.append("sw $v0, " + getLocation(t) + "($sp)\n");
			}
			tempInMem.put(t, getLocation(t));
			if(!canFullReg()){
				tReg=tempInReg.get(t);
				if(tReg!=null){
					reg2temp.get(tReg).remove(t);
				}
				tempInReg.remove(t);
			}
			entryBlocksSysCall(true, q.instructionNo, b, true, (SysCall)q);

			return ;
		}else if(q instanceof Jump){
			exitBlocks(b,false);
			ans.append( q.toAsm()+"\n");		
			return ;
		}else if(q instanceof LabelQuad){
			ans.append( q.toAsm()+"\n");
			return ;
		}else if(q instanceof Move){
			Oprand src=((Move)q).src,dst=((Move)q).dst;
			getReg2(dst,src,false,b,q.instructionNo);
			return ;
		}else if(q instanceof Return){
			Oprand rv=((Return)q).value;
			if(rv instanceof Const){
				int value=((Const)((Return)q).value).value;
				if(value<32767&&value>-32768)ans.append("addiu $v0, $zero," +((Const)((Return)q).value).value + "\n");
				else ans.append("li $v0, " +((Const)((Return)q).value).value + "\n");
			} else if(rv instanceof TempOprand){
				Temp t=((TempOprand)rv).temp;
				String tReg=tempInReg.get(t);
				if(tReg==null){
					ans.append("lw $v0, " + getLocation(((TempOprand)((Return)q).value).temp) + "($sp)\n");
				}else
					ans.append("move $v0,"+tReg+"\n");
			} else if(rv instanceof Mem){
				Temp t=((Mem)rv).base;
				String tReg=tempInReg.get(t);
				String opcode=((Mem)rv).length==4?"lw ":"lb ";
				if(tReg==null){
					ans.append("lw $v0, " + getLocation(((Mem)rv).base) + "($sp)\n");
					ans.append(opcode+"$v0, " + ((Mem)rv).offset + "($v0)\n");
				}else{
					ans.append(opcode+"$v0, " + ((Mem)rv).offset + "("+tReg+")\n");
				}
			} else error("2012!");
			if(!((Return)q).isFuncExit)ans.append("j _"+name.toString()+"_exit\n");
			ans.append( q.toAsm()+"\n");
			return ;
		}else if(q instanceof UnaryOp){
			String [] regs=new String[2];
			Oprand src=((UnaryOp)q).src,dst=((UnaryOp)q).dst;
			javac.absyn.UnaryOp op = ((UnaryOp)q).op;
			regs=getReg2(dst,src,true,b,q.instructionNo);
			ans.append( UnaryOp.getAsm(op)+" "+ regs[0] +"," +regs[1]+"\n");
			tempInMem.remove(((TempOprand)dst).temp);
			return ;
		}else {
			error("2012!");
		}
		return ;
	}
	

	//works only for UnaryOp and Move, as a helper of getReg2
	private String dstInRegAndOk(Temp t, boolean spill){
		String ret=tempInReg.get(t);
		if(ret!=null&&reg2temp.get(ret).size()>1){//ok?
			if(spill)
				spill2Mem(t,false);
			reg2temp.get(ret).remove(t);
			ret=null;
		}
		return ret;
	}
	
	private void spill2Mem(Temp t,boolean isFullReg){
		if(t.isOnceTemp)return;
		if(tempInMem.get(t)==null||isFullReg){//TODO:isFullReg->isFullReg&&nothing t.def reaches here
			ans.append("sw "+tempInReg.get(t)+","+getLocation(t)+"($sp)\n");
			tempInMem.put(t,getLocation(t));
		}
	}
	
	private String[] getReg2(Oprand dst, Oprand src, boolean isUnaryOp,BasicBlock b,int insno) {
		String [] ret=new String[2];
		Temp t0=null,t1=null;

		if (dst instanceof Mem){//always !isUnaryOp
			t0=((Mem)dst).base;
			//ret[0]=dstInRegAndOk(out,t0, true);//FIXME:what's this?
			ret[0]=tempInReg.get(t0);
			ret[0]=getRegHelper(null,ret[0],ret[1],t0,dst,src,b,insno);
			if(src instanceof TempOprand){
				if(!isUnaryOp){
					t1=((TempOprand)src).temp;
					ret[1]=tempInReg.get(t1);
					ret[1]=getRegHelper(null,ret[1],ret[0],t1,src,dst,b,insno);
					tempInReg.put(t0, ret[0]);
					reg2temp.get(ret[0]).add(t0);
					tempInReg.put(t1, ret[1]);
					reg2temp.get(ret[1]).add(t1);
					ans.append("sw "+ret[1]+","+((Mem)dst).offset+"("+ret[0]+")\n");
				}
				else  error("meow");
			}else if(src instanceof Const){
				int value=((Const)src).value;
				tempInReg.put(t0, ret[0]);
				if(!isUnaryOp){
					if(value!=0){
						if(value<32767&&value>-32768)ans.append("addiu $k0,$zero, " + ((Const)src).value + "\n");
						else ans.append("li $k0, " + ((Const)src).value + "\n");
						ret[1]="$k0";
					}else ret[1]="$zero";
					ans.append("sw "+ret[1]+","+((Mem)dst).offset+"("+ret[0]+")\n");
				}else error("meow....");
			}else if(src instanceof Mem){
				if(!isUnaryOp){
					t1=((Mem)src).base;
					ret[1]=tempInReg.get(t1);
					ret[1]=getRegHelper(null,ret[1],ret[0],t1,src,dst,b,insno);
					tempInReg.put(t0, ret[0]);
					reg2temp.get(ret[0]).add(t0);
					tempInReg.put(t1, ret[1]);
					reg2temp.get(ret[1]).add(t1);
					String opcode=((Mem)src).length==4?"lw ":"lb ";
					ans.append(opcode+"$k0,"+((Mem)src).offset+"("+ret[1]+")\n");
					ans.append("sw $k0,"+((Mem)dst).offset+"("+ret[0]+")\n");
				}else  error("meow@mem");
			}else error("meow..."+dst+"<-"+src);
		} else {
			t0=((TempOprand)dst).temp;
			ret[0]=dstInRegAndOk(t0,false);
			ret[0]=getRegHelper2(ret[0],t0,b,insno,(src instanceof Mem)?
					((Mem)src).base:
						(src instanceof TempOprand&&!isUnaryOp&&canFullReg())?
								((TempOprand)src).temp:null);
			if(src instanceof TempOprand){
				t1=((TempOprand)src).temp;
				ret[1]=tempInReg.get(t1);
				if(ret[1]!=null&&!isUnaryOp&&!canFullReg()){//src in Reg
					reg2temp.get(ret[0]).remove(t0);
					tempInMem.remove(t0);
					tempInReg.put(t0, ret[1]);
					reg2temp.get(ret[1]).add(t0);
					ret[0]=ret[1];
					return ret;
				}else if(ret[1]!=null&&!isUnaryOp&&canFullReg()){
					tempInMem.remove(t0);
					ans.append("move "+ret[0]+", "+ret[1]+"\n");
					return ret;
				}
				
				ans.append("lw "+ ret[0] +","+getLocation(t1)+"($sp)\n");
				
				if(!isUnaryOp){
					tempInMem.remove(t0);
					if(!canFullReg()){
						tempInReg.put(t1, ret[0]);
						reg2temp.get(ret[0]).add(t1);
					}
					ret[1]=ret[0];
				}else {
					ret[1]=ret[0];
					return ret;
				}
			}else if(src instanceof Mem){
				t1=((Mem)src).base;
				int length=((Mem)src).length;
				ret[1]=tempInReg.get(t1);
				String opcode=length==4?"lw ":"lb ";
				ret[1]=getRegHelper(dst,ret[1],null,t1,src,null,b,insno);
				ans.append(opcode+ret[0]+","+((Mem)src).offset + "("+ret[1]+")\n");
				tempInMem.remove(t0);
				tempInReg.put(t0, ret[0]);
				if(!isUnaryOp){
					
				}else{
					ret[1]=ret[0];
					return ret;
				}
			}else if(src instanceof Const){
				int value=((Const)src).value;
				ret[1]=String.valueOf(value);
				if(!isUnaryOp||value!=0){
					if(value<32767&&value>-32768)ans.append("addiu "+ret[0]+",$zero, " + ((Const)src).value + "\n");
					else ans.append("li "+ret[0]+", " + ((Const)src).value + "\n");
					tempInMem.remove(t0);
				}else{
					ret[1]="$zero";
				}
			}else if(src instanceof LabelAddress){
				if(!isUnaryOp){
					ans.append("la "+ret[0]+", " + ((LabelAddress)src).label + "\n");
					tempInMem.remove(t0);
				}else{
					error("meow!!!");
				}
			}
		}
		return ret;
	}

	private int min(int a, int b) {
		return a<b?a:b;
	}

	//BinOp or Move: no a[i]=a[j] or a[i]=x+y form.
	//if src is not const then it's loaded otherwise it's not.
	//this works only for BinOp and Branch
	private String[] getReg(BinaryOp op,Oprand dst,Oprand src1,Oprand src2,boolean canFoldByShift,BasicBlock b,int insno) {
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
		ret[1]=getRegHelper(dst,ret[1],ret[2],t1,src1,src2,b,insno);
		
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
			ret[2]=getRegHelper(dst,ret[2],ret[1],t2,src2,src1,b,insno);
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
		if(src2 instanceof Const&&op==BinaryOp.MINUS){
			ret[2]=Integer.toString(-((Const)src2).value);
		}
		if(canFoldByShift)
			ret[2]=Integer.toString(BinOp.log2(((Const)src2).value));
		
		if(ret[1].length()>=2&&ret[1].substring(0, 2).equals("$k")){
			int value=((Const)src1).value;
			if(value<32767&&value>-32768)ans.append("addiu "+ret[1]+",$zero, " + ((Const)src1).value + "\n");
			else ans.append("li "+ret[1]+", " + ((Const)src1).value + "\n");
		}
		if(ret[2].length()>=2&&ret[2].substring(0, 2).equals("$k")){
			int value=((Const)src2).value;
			if(value<32767&&value>-32768)ans.append("addiu "+ret[2]+",$zero, " + ((Const)src2).value + "\n");
			else ans.append("li "+ret[2]+", " + ((Const)src2).value + "\n");
		}

		if(dst!=null){
			if(dst instanceof TempOprand){
				t0=((TempOprand)dst).temp;
				ret[0]=tempInReg.get(t0);
				if(ret[0]!=null&&reg2temp.get(ret[0]).size()>1){//ok?
					if(tempInMem.get(t0)==null){
						//out.append("sw "+ret[0]+","+getLocation(t0)+"($sp)\n");
						tempInMem.put(t0,getLocation(t0));
					}
					reg2temp.get(ret[0]).remove(t0);
					ret[0]=null;
				}
			}else
				error("2012!getReg!dst");
			//TODO:favor choosing src1 or src2 if they're dead.
			ret[0]=getRegHelper2(ret[0],t0,b,insno,null);
		}
		

		if(src1 instanceof Mem){
			String opcode=((Mem)src1).length==4?"lw ":"lb ";
			ans.append(opcode+"$k0, "+ ((Mem)src1).offset+ "("+ret[1]+")\n");
			ret[1]="$k0";
		}
		if(src2 instanceof Mem){
			String opcode=((Mem)src2).length==4?"lw ":"lb ";
			ans.append(opcode+"$k1, "+ ((Mem)src2).offset+ "("+ret[2]+")\n");
			ret[2]="$k1";
		}
		
		return ret;
	}
	
	//this works particularly for selecting dst
	private String getRegHelper2(String ret1,Temp t1,BasicBlock b,int insno,Temp srcTemp) {
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
							if(rt==null)continue;
							boolean ok=tempInMem.get(rt)!=null
									||rt.equals(t1)
									||(!hasNextUseAfter(b,rt,insno-1))
									;//TODO: check or rt is dead
							if(!ok){
								curSTcnt++;
								if(!stackPtr.containsKey(rt))curSTcnt+=31;
								curSTInstr+="sw "+v+", "+ getLocation(rt)+ "($sp)\n";
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
				ans.append(stInstr);
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
		}else {
			tempInReg.put(t1, ret1);
			HashSet<Temp> tmp=new HashSet<Temp>();
			tmp.add(t1);
			reg2temp.put(ret1, tmp);
		}
		
		return ret1;
	}
	
	//this works particularly for BinOp and Branch
	private String getRegHelper(Oprand dst,String ret1,String ret2,Temp t1,Oprand src1,Oprand src2,BasicBlock b,int insno) {
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
							if(rt==null)continue;
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
									||(!hasNextUseAfter(b,rt,insno-1)&&(src2T==null||rt!=src2T))
									;//TODO:check or rt is dead
							if(!ok){
								curSTcnt++;
								if(!stackPtr.containsKey(rt))curSTcnt+=31;
								curSTInstr+="sw "+v+", "+ getLocation(rt)+ "($sp)\n";
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
				ans.append(stInstr);
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
				ans.append("lw "+ret1+", "+ getLocation(t1)+ "($sp)\n");
			}else{
				tempInReg.put(t1, ret1);
				HashSet<Temp> tmp=new HashSet<Temp>();
				tmp.add(t1);
				reg2temp.put(ret1, tmp);
				ans.append("lw "+ret1+", "+ getLocation(t1)+ "($sp)\n");
			}
		}
		return ret1;
	}
	
	public boolean canFullReg(){
		//return false;
		return stackPtr.size()<=totalRegSize&&
				hasCall<=4&&
				(argv.length<6||(argv.length==6&&hasSysCall<argv.length)||(argv.length==11&&hasCall<=1))&&
				hasCall*argv.length<12&&
				(body.size()>3||hasSysCall==0);
	}
	public boolean canFullReg1(){
		//return false;
		return stackPtr.size()<=totalRegSize-4;
	}
	
	public static void error(String string) {
		throw new SException(string);
	}
}
