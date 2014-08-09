package org.chen.p2p;

import java.io.File;
import java.io.Serializable;

class FileMetadata implements Serializable{
	private String filename;//文件名字
	private long filelength;//文件长度
	final private int chunksize=1024*1024;//chunk的大小，固定位1MB大小
	private long lastchunksize;//最后一个chunk的大小
	private long chunknum;//chunk的数目
	public FileMetadata(long filesize,long lastchunksize,long chunknum){
		this.chunknum=chunknum;
		this.filelength=filesize;
		//this.chunksize=(int) chunksize;
		this.lastchunksize=lastchunksize;
	}
	public FileMetadata(String filename,String path){
		System.out.println("get meatdata from "+filename);
		File f = new File(path+File.separator+filename);
		if(f.exists() && f.isFile()){
			this.filename=filename;
			filelength=f.length();
			if(filelength%chunksize==0){
				chunknum=filelength/chunksize;
				lastchunksize=chunksize;
			}
			else{
				chunknum=filelength/chunksize+1;
				lastchunksize = filelength%chunksize;
			}
			
		}
		else{
			filename=null;
			filelength=-1;
			chunknum=-1;
			lastchunksize=-1;
			System.out.println(filename+" is not a file");
		}
	}
	public long fileLength(){
		return this.filelength;
	}
	public long chunkNum(){
		return this.chunknum;
	}
	public long lastChunkSize(){
		return this.lastchunksize;
	}
	public String fileName(){
		return this.filename;
	}
	public Long chunkSize(){
		return (long) this.chunksize;
	}
	public static void main(String args[]){
		String file="D:\\eclipse\\eclipse-jee-luna-R-win32-x86_64.zip";
		FileMetadata metadata = new FileMetadata(file,"");
		System.out.println(metadata.fileLength());
		System.out.println(metadata.chunkNum());
		System.out.println(metadata.chunkSize());
		System.out.println(metadata.lastChunkSize());
	}
}
