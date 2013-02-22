package javac.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import javac.quad.Call;
import javac.quad.Quad;
import javac.quad.Temp;
import javac.trans.FuncFrag;

public class LiveAnalysis {
	public static boolean enabled=true;
	public static boolean semiEnabled=true;
	public static HashMap<Temp,Integer > nextDef=new HashMap<Temp,Integer > ();
	public static HashMap<Temp,Integer > nextUse=new HashMap<Temp,Integer > ();
	
	public static LinkedList<BasicBlock> semiLiveAnalysis(LinkedList<BasicBlock> blocks) {
		for(BasicBlock b: blocks){
			if(b.killed)continue;
			nextDef=new HashMap<Temp,Integer > ();
			nextUse=new HashMap<Temp,Integer > ();
			Iterator<Quad> i=b.instr.descendingIterator();
			while(i.hasNext()){
				Quad q=i.next();
				if(q.killed)continue;
				if(q.def!=null){
					for(Temp t:q.def){
						Integer qnext0=nextDef.get(t),qnext1=nextUse.get(t);
						if(qnext1==null||(qnext0!=null&&qnext0<qnext1)){
							if(q instanceof Call&&FuncFrag.isSideEffectCall((Call)q)){
								q.killed=false;
								((Call)q).killedret=true;
							}else {
								if(qnext0==null&&b.isLiveOut(t)){
									q.killed=false;
								}else{
									q.killed=true;
								}
							}
						}else
							nextDef.put(t, q.instructionNo);
					}
				}
				if(q.use!=null&&!q.killed){
					for(Temp t:q.use){
						nextUse.put(t, q.instructionNo);
					}
				}
			}
		}
		return blocks;
	}
	public static LinkedList<BasicBlock> liveAnalysis(LinkedList<BasicBlock> blocks) {
		boolean changed =true;
		if(!semiEnabled)return blocks;
		if(!enabled)return semiLiveAnalysis(blocks);
		while(changed){
			changed=false;
			for(BasicBlock b: blocks){
				if(b.killed)continue;
				for(BasicBlock v:b.succ){
					if(v.killed)continue;
					b.liveOut.addAll(v.liveIn);
				}
				HashSet<Temp> newIn = new HashSet<Temp>(b.liveOut);
				newIn.removeAll(b.def);
				newIn.addAll(b.use);
				if(!b.liveIn.containsAll(newIn)){
					changed=true;
					b.liveIn=newIn;
				}
			}
		}
		return semiLiveAnalysis(blocks);
	}
}
