package org.chen.p2p;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tomp2p.futures.FutureDHT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

//������������ĳ��chunk��holder�б���˭�У�ID��ʽΪ<ip:port>
//����Щ��������Щ�˿��ϣ������õ���chunk���ݡ�
//��Ȩ�ޡ�
class ChunkHolderList {
	private List<String> ip_pors;  //ӵ�и�chunk�Ļ����б�
	private String filename;       //�ļ�����
	private long chunk_num;        //���ļ���chunk��Ŀ
	private long chunk_id;         //���chunk�ı��
	private long normalchunk_size; //����chunk�Ĵ�С��
	private long thischunk_size;   //���chunk�Ĵ�С
	
	
	/*
	 * ˢ��Ip_Ports�����������ʵʱģʽ������õ�����ǰ�������ǣ���ͳͳ�ļ����
	 * ����chunk��ip_ports��Ȼ�󴫵ݸ�20���̣߳�ÿ���߳̿�ʼ����������Ĳ��֣������ǣ�
	 * ��20���߳��������ع��̺󣬲��ٸ���ip_ports������������������ӵ��ṩ�߲��ᱻѡ�У�
	 * ����Ǳ�ڵĵ�������Ƚ����ء�
	 * ����취���ǣ����������ؿ�ʼ֮ǰ��ˢ��ip_ports���õ����µ����ݡ������Ľ���ǣ�ʵʱ�Ը���һ�㣬
	 * ��������Ҳ��õ�һ���̶ȵĻ��⡣
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
