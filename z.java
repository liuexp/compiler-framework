record z{
	int x;
}
int main(){
	int a;	int b;
	//int c,d;
	int i;
	string c;
	char d;
	char eol;
	z e;
	int [] f;
	int [][] g;
	g=new int[][2];
	g[0]=new int[6];
	g[1]=new int[6];
        e=new z;
	a=3;
	b=a%4+12;
	a=0;
	b=a%4+12;
	a=4;
	b=a%4+12;
	a=3;
	b=a%4+12;
        e.x=b+a;
        c="asdfqwerabc\n";
      	eol='\n';
      	f = new int [5];
        d='我';
        printString(c);
        printInt(a);
        printChar(eol);
        printInt(b);
        printChar(eol);
        printInt(e.x);
        printChar(eol);
        printChar(c[0]);
        c=c+"\n";
        for(i=0;i<5;i=i+1){
        	printString(c+"\n");
        	a=a+1;
        }
        printChar(eol);
        printString(a+"\n");
  
        f[0]=1;
        printInt(f[0]);
        printInt(f[1]);
        printInt(f[2]);
        printInt(f.length);
  	for(i=0;i<6;i=i+1){
  		g[0][i]=i+1;
  	}
  	for(i=0;i<6;i=i+1){
  		printInt(g[0][i]);
  		printInt(g[1][i]);
  	}
}
