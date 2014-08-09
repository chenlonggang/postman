package org.chen.p2p;

import java.io.Serializable;

//���ཫ����Ϊ������������л���socket�У�����Ҫ����Щ����
/*
 * ������������ʵ�ִ������ز����ġ�
 * filename :String,Ҫϵ�ڵ��ļ���
 * chunknum :long,����ļ���chunk��
 * chunkid  :long,��Ҫ���ص�chunk�ı��
 * chunksize:long,��Ҫ���ص�chunk�Ĵ�С����λB
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
