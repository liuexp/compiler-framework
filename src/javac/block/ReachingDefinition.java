package javac.block;

import java.util.HashSet;
import java.util.LinkedList;

import javac.quad.Temp;
//FIXME:looks like this is deprecated, I didn't find any use for this.
public class ReachingDefinition {
	public static boolean enabled=false;
	public static LinkedList<BasicBlock> reachingDefinition(LinkedList<BasicBlock> blocks) {
		boolean changed =true;
		while(changed){
			changed=false;
			for(BasicBlock b: blocks){
				if(b.killed)continue;
				for(BasicBlock v:b.succ){
					if(v.killed)continue;
					b.reachIn.addAll(v.reachOut);
				}
				HashSet<Temp> newOut = new HashSet<Temp>(b.reachIn);
				newOut.removeAll(b.kill);
				newOut.addAll(b.gen);
				if(!b.reachOut.containsAll(newOut)){
					changed=true;
					b.reachOut=newOut;
				}
			}
		}
		return blocks;
	}
}
