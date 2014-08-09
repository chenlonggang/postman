package org.chen.p2p;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Future;

public class ShellApp {
	private static DHTPeer peer=null;
	private static int id = 0;
	public static void main(String args[]){
		
		if(args.length!=1){
			System.out.println("start it like this: ShellApp id");
			System.exit(0);
		}
		try{
			id=Integer.parseInt(args[0]);
		}catch(Exception e){
			e.printStackTrace();
		}
		try {
			peer=new DHTPeer(id);
			peer.StartDownLoadService();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		sayHello();
		if(peer!=null){
			while(true){
				String request = getrequest();
				Exec(request);
			}
		}
		
	}
	
	public static void sayHello(){
		System.out.println("The folling command is provided:");
		System.out.println("share filename");
		System.out.println("download filename");
		System.out.println("ls");
		System.out.println("quit");
	}
	public static String getrequest(){
		System.out.print(">");
		Scanner in = new Scanner(System.in);
		String request  =in.nextLine();
		if(request.startsWith("share") || request.startsWith("download")||request.startsWith("ls")||request.startsWith("quit")){
			return request;
		}else{
			return getrequest();
		}
	}
	public static void Exec(String op){
		if(op.equals("quit")){
			if(peer!=null)
				peer.shutDown();
			System.exit(0);
		}
		else if(op.startsWith("share")){
			try{
			    String [] parts = op.split(" ");
			    if(parts.length!=2)
			    	return ;
			    else{
			    	String file =parts[1].trim();
			    	peer.share(file);
			    }
			} catch (ClassNotFoundException | IOException
					| InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return ;
			}
		}else if(op.startsWith("download")){
			try{
				String [] parts = op.split(" ");
				if(parts.length!=2)
					return ;
				else{
					String file=parts[1];
					DownLoadState state = new DownLoadState();
					Future<String> f=peer.DownLoad(file,state);
					while(!state.isIsdone()){
						Thread.sleep(500);
					
						System.out.println("%"+state.getPercent());
					}
					System.out.println("%"+state.getPercent());
					System.out.println(f.get());
				}
			}catch(Exception e){
				e.printStackTrace();
				return ;
			}
		}else if(op.equals("ls")){
			String path =peer.getCurrentPath();
			if(!path.endsWith(File.separator)){
				path=path+File.separator;
			}
			File root = new File(path);
			if(!root.exists() && !root.isDirectory()){
				System.out.println("ls failed");
			}
			String [] files = root.list();
			for(String f:files){
				System.out.println(f);
			}
		}
		else{
			sayHello();
		}
	}
}

