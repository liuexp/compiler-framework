package javac.env;
import javac.semantic.SException;
import javac.symbol.Symbol;
import java.util.*;

public class Env{
	public Map<Symbol,Entry> env;
	public Env(){
		env = new HashMap<Symbol,Entry>();
	}
	public Env(Map<Symbol, Entry> env2) {
		env=new HashMap<Symbol,Entry>(env2);
	}
	public Entry get(Symbol n){
		if(!env.containsKey(n))throw new SException("No such symbol names."+n.toString());
		return env.get(n);
	}
	public Entry getWithoutException(Symbol n){
		return env.get(n);
	}
	public void put(Symbol n,Entry e){
		if(env.containsKey(n))throw new SException("Duplicate symbol names."+n.toString());
		env.put(n,e);
	}
	public boolean has(Symbol n){
		return env.containsKey(n);
	}
	public Env cloneEnv(){
		return new Env(env);
	}

};
