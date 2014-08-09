package org.chen.p2p;

import java.io.Serializable;

//该类将会作为“命令对象”序列化到socket中，里面要放这些参数
/*
 * 这个类就是用老实现传递下载参数的。
 * filename :String,要系在的文件名
 * chunknum :long,这个文件的chunk数
 * chunkid  :long,想要下载的chunk的编号
 * chunksize:long,想要下载的chunk的大小，单位B
 */
public class GetChunk implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String filename;
	private long chunknum;
	private long chunkid;
	private long normalchunksize;
	private long thischunksize;
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public long getChunknum() {
		return chunknum;
	}
	public void setChunknum(long chunknum) {
		this.chunknum = chunknum;
	}
	public long getChunkid() {
		return chunkid;
	}
	public void setChunkid(long chunkid) {
		this.chunkid = chunkid;
	}
	public long getNormalChunksize() {
		return normalchunksize;
	}
	public void setNormalChunksize(long chunksize) {
		this.normalchunksize = chunksize;
	}
	public long getThisChunkSize(){
		return this.thischunksize;
	}
	public void setThisChunkSize(long size){
		this.thischunksize=size;
	}
	public GetChunk(String filename, long chunknum, long chunkid, long normalchunksize,long thischunksize) {
		super();
		this.filename = filename;
		this.chunknum = chunknum;
		this.chunkid = chunkid;
		this.normalchunksize = normalchunksize;
		this.thischunksize=thischunksize;
	}
	

}
