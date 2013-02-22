package javac.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javac.absyn.BinaryOp;
import javac.linearScan.Interval;
import javac.quad.BinOp;
import javac.quad.Branch;
import javac.quad.Call;
import javac.quad.Const;
import javac.quad.Jump;
import javac.quad.Label;
import javac.quad.LabelQuad;
import javac.quad.Mem;
import javac.quad.Move;
import javac.quad.Oprand;
import javac.quad.Quad;
import javac.quad.Return;
import javac.quad.Temp;
import javac.quad.TempOprand;
import javac.quad.UnaryOp;
import javac.trans.FuncFrag;

public class BasicBlock {
	public LinkedList<Quad> instr=new LinkedList<Quad> ();
	public LinkedList<BasicBlock> pred=new LinkedList<BasicBlock>();
	public LinkedList<BasicBlock> succ=new LinkedList<BasicBlock>();
	
	//TODO: use java.util.BitSet?
	public HashSet<Temp> liveIn=new HashSet<Temp>();
	public HashSet<Temp> liveOut=new HashSet<Temp>();
	public HashSet<Temp> reachIn=new HashSet<Temp>();
	public HashSet<Temp> reachOut=new HashSet<Temp>();
	
	public HashSet<Temp> use=new HashSet<Temp>();
	public HashSet<Temp> def=new HashSet<Temp>();
	public HashSet<Temp> gen=new HashSet<Temp>();
	public HashSet<Temp> kill=new HashSet<Temp>();
	public HashMap<Temp,Interval> liveRange=new HashMap<Temp,Interval>();
	public HashMap<Interval,Integer> framePtr=new HashMap<Interval,Integer> ();
	public int frameSize=0;
	public boolean killed=false;
	public boolean curLiveEnabled=false;
	//a quad q is killed iff q.nextdef<q.nextuse
	
	public static Map<Label,Label> labelMark=new HashMap<Label,Label>();
	
	public BasicBlock(LinkedList<Quad> i){
		instr=i;
		curLiveEnabled=LiveAnalysis.enabled;
	}
	public BasicBlock(){
		curLiveEnabled=LiveAnalysis.enabled;
	}
	
	public void addEdge(BasicBlock v){
		if(v==null)return;
		v.pred.add(this);
		this.succ.add(v);
	}
	
	public boolean isLiveOut(Temp t){
		if(t==null)return false;
		if((curLiveEnabled||LiveAnalysis.enabled)&&liveOut!=null){
			return liveOut.contains(t);
		}
		return true;
	}
	
	public void updateSrange(Temp t,int s){
		if(liveRange.containsKey(t)){
			liveRange.get(t).startpoint=
					liveRange.get(t).startpoint<0?
							s:(liveRange.get(t).startpoint>s?
									s:liveRange.get(t).startpoint);
		}else{
			liveRange.put(t, new Interval(s,-1));
		}
	}
	public void updateErange(Temp t,int e){
		if(liveRange.containsKey(t)){
			liveRange.get(t).endpoint=
					liveRange.get(t).endpoint<0?
							e:(liveRange.get(t).endpoint<e?
									e:liveRange.get(t).endpoint);
		}else{
			liveRange.put(t, new Interval(-1,e));
		}
	}
	public static LinkedList<Quad> killLabel(LinkedList<Quad> x){
		LinkedList<Quad> ret=new LinkedList<Quad>();
		labelMark=new HashMap<Label,Label>();
		HashSet<Label> usedLabel=new HashSet<Label>();
		HashSet<LabelQuad> allLabel=new HashSet<LabelQuad>();
		Quad lastQ=null;
		for(Quad q:x){
			if(q.killed)continue;
			if(q instanceof LabelQuad){
				allLabel.add((LabelQuad) q);
				if(lastQ!=null&&lastQ instanceof LabelQuad){
					q.killed=true;
					labelMark.put(((LabelQuad)q).label, getRelabel(((LabelQuad)lastQ).label));
				}
			}
			lastQ=q;
		}
		lastQ=null;
		for(Quad q:x){
			if(q.killed)continue;
			Quad newq=relabel(q);
			Label newlabel=null;
			//ret.add(newq);
			if(q instanceof Branch){
				newlabel=getRelabel(((Branch)newq).label);
			}else if(q instanceof Jump){
				newlabel=getRelabel(((Jump)newq).label);
				if(lastQ!=null&&lastQ instanceof Jump){
					q.killed=true;
				}
			}else if(q instanceof LabelQuad){
				newlabel=getRelabel(((LabelQuad)newq).label);
				if(lastQ!=null&&lastQ instanceof Jump&&newlabel.equals(((Jump)lastQ).label)){
					lastQ.killed=true;
				}
				newlabel=null;
			}
			lastQ=q;
		}
		for(Quad q:x){
			if(q.killed)continue;
			Quad newq=relabel(q);
			Label newlabel=null;
			ret.add(newq);
			if(q instanceof Branch){
				newlabel=getRelabel(((Branch)newq).label);
			}else if(q instanceof Jump){
				newlabel=getRelabel(((Jump)newq).label);
				if(lastQ!=null&&lastQ instanceof Jump){
					q.killed=true;
					newlabel=null;
				}
			}else if(q instanceof LabelQuad){
				newlabel=getRelabel(((LabelQuad)newq).label);
				if(lastQ!=null&&lastQ instanceof Jump&&newlabel.equals(((Jump)lastQ).label)){
					lastQ.killed=true;
				}
				newlabel=null;
			}
			if(newlabel!=null)usedLabel.add(newlabel);
			lastQ=q;
		}
		for(LabelQuad q:allLabel){
			if(q.killed)continue;
			if(!usedLabel.contains(q.label)){
				q.killed=true;
			}
		}
		return ret;
	}
	
	public static Quad relabel(Quad q) {
		if(q instanceof Branch){
			((Branch)q).label=getRelabel(((Branch)q).label);
		}else if(q instanceof Jump){
			((Jump)q).label=getRelabel(((Jump)q).label);
		}else if(q instanceof LabelQuad){
			((LabelQuad)q).label=getRelabel(((LabelQuad)q).label);
		}
		return q;
	}
	public static Label getRelabel(Label x){
		if(labelMark.containsKey(x))return labelMark.get(x);
		return x;
	}
	
	public static LinkedList<BasicBlock> buildBlocks(LinkedList<Quad> body){
		BasicBlock curB=new BasicBlock();
		LinkedList<BasicBlock> blocks=new LinkedList<BasicBlock>();
		Map<Label,BasicBlock> label2Block=new HashMap<Label,BasicBlock>();
		boolean isLastJ=false,isLastR=false;
		BasicBlock nextB=null;
		int insno=0;
		for(Quad q: body) {
			if(q.killed)continue;
			if(isLastJ|| q instanceof LabelQuad){
				blocks.add(curB);
				nextB=new BasicBlock();
				insno=0;
				if(isLastR||( q instanceof LabelQuad&&!isLastJ)){
					//if(!(curB.instr.peekLast() instanceof Jump))
						curB.addEdge(nextB);
				}
				curB=nextB;
			}
			if(q instanceof LabelQuad){
				label2Block.put(((LabelQuad) q).label, curB);
			}
			q.instructionNo=insno;
			
			curB.instr.add(q);
			if(q.def!=null){
				for(Temp t:q.def){
					if(t==null)continue;
					if(!t.isOnceTemp)continue;
					t.liveStart=min(t.liveStart,insno);
					t.liveEnd=max(t.liveEnd,insno);
				}
			}
			if(q.use!=null){
				for(Temp t:q.use){
					if(!t.isOnceTemp)continue;
					t.liveStart=min(t.liveStart,insno);
					t.liveEnd=max(t.liveEnd,insno);
				}
			}
			if(LiveAnalysis.enabled||LiveAnalysis.semiEnabled){
				HashSet<Temp> def=null,use=null;
				if(q.def!=null){
					for(Temp t:q.def){
						curB.updateSrange(t,insno);
						curB.updateErange(t,insno);
					}
					//q.def.removeAll(curB.use);
					def=new HashSet<Temp>(q.def);
					def.removeAll(curB.use);
				}
				if(q.use!=null){
					for(Temp t:q.use){
						curB.updateSrange(t,insno);
						curB.updateErange(t,insno);
					}
					//q.use.removeAll(curB.def);
					use=new HashSet<Temp>(q.use);
					use.removeAll(curB.def);
				}
				if(def!=null)curB.def.addAll(def);
				if(use!=null)curB.use.addAll(use);
			}
			
			isLastJ=(q instanceof Branch||(q instanceof Call&&!FuncFrag.isNativeCallsInReg((Call)q))||q instanceof Jump||q instanceof Return);
			isLastR=(q instanceof Branch||(q instanceof Call&&!FuncFrag.isNativeCallsInReg((Call)q)));
			insno++;
		}
		blocks.add(curB);
		for(BasicBlock b: blocks){
			Quad q=b.instr.peekLast();
			if(q instanceof Branch){
				b.addEdge(label2Block.get(((Branch)q).label));
			/*}else if(q instanceof Call){//in case of self recursive
				b.addEdge(label2Block.get(((Call)q).function));
				*/
			}else if(q instanceof Jump){
				b.addEdge(label2Block.get(((Jump)q).label));
			}
			
		}
		return blocks;
	}
	private static int max(int liveEnd, int insno) {
		return liveEnd<0?insno:liveEnd<insno?insno:liveEnd;
	}
	private static int min(int liveStart, int insno) {
		return liveStart<0?insno:liveStart<insno?liveStart:insno;
	}

}
