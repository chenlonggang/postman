package org.chen.p2p;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tomp2p.futures.FutureDHT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

//该类用来保存某个chunk的holder列表，就谁有：ID格式为<ip:port>
//在这些机器的这些端口上，有望得到该chunk数据。
//包权限。
class ChunkHolderList {
	private List<String> ip_pors;  //拥有该chunk的机器列表
	private String filename;       //文件名字
	private long chunk_num;        //该文件的chunk数目
	private long chunk_id;         //这个chunk的编号
	private long normalchunk_size; //其他chunk的大小。
	private long thischunk_size;   //这个chunk的大小
	
	
	/*
	 * 刷新Ip_Ports，这个方法在实时模式里面会用到，先前的做法是：先统统的计算出
	 * 所有chunk的ip_ports，然后传递给20个线程，每个线程开始现在他负责的部分，问题是：
	 * 在20个线程启动下载过程后，不再更新ip_ports，在这个过程中新增加的提供者不会被选中，
	 * 所以潜在的单点问题比较严重。
	 * 解决办法就是：在真正下载开始之前，刷新ip_ports，得到最新的数据。这样的结果是：实时性更好一点，
	 * 单点问题也会得到一定程度的缓解。
	 */
	public List<String> RefreshIp_Ports(Peer peer) {
		ip_pors=null;
		FutureDHT chunkDHT=peer.get(Number160.createHash(filename+"_"+chunk_id)).setAll().start();
		chunkDHT.awaitUninterruptibly();
		if(chunkDHT.isSuccess()){
			Map<Number160,Data> results = chunkDHT.getDataMap();
			if(results.size()>0){
				ip_pors=new ArrayList<String>();
				for(Map.Entry<Number160, Data> entry:results.entrySet()){
					try {
						ip_pors.add(entry.getValue().getObject().toString());
					} catch (ClassNotFoundException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return ip_pors;
	}
	
	public void setNormalChunkSize(Long long1){
		this.normalchunk_size=(long)long1;
	}
	public long getNormalChunksize(){
		return normalchunk_size;
	}
	public void setThisChunkSize(long size){
		this.thischunk_size=size;
	}
	public long getThisChunkSize(){
		return this.thischunk_size;
	}
	public List<String> getIp_pors() {
		return ip_pors;
	}
	public void setIp_pors(List<String> ip_pors) {
		this.ip_pors = ip_pors;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public long getChunk_num() {
		return chunk_num;
	}
	public void setChunk_num(long chunk_num) {
		this.chunk_num = chunk_num;
	}
	public long getChunk_id() {
		return chunk_id;
	}
	public void setChunk_id(long chunk_id) {
		this.chunk_id = chunk_id;
	}
	public ChunkHolderList(List<String> ip_pors, String filename,
			long chunk_num, long chunk_id) {
		super();
		this.ip_pors = ip_pors;
		this.filename = filename;
		this.chunk_num = chunk_num;
		this.chunk_id = chunk_id;
	}
	public ChunkHolderList() {
		// TODO Auto-generated constructor stub
		ip_pors = new ArrayList();
	}
	
	
}
