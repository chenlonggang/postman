package org.chen.p2p;

import java.io.File;
import java.io.Serializable;

class FileMetadata implements Serializable{
	private String filename;//�ļ�����
	private long filelength;//�ļ�����
	final private int chunksize=1024*1024;//chunk�Ĵ�С���̶�λ1MB��С
	private long lastchunksize;//���һ��chunk�Ĵ�С
	private long chunknum;//chunk����Ŀ
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
