package org.chen.p2p;

import java.util.Map;
import java.io.IOException;
import net.tomp2p.futures.FutureDHT;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

public class ExampleSimple {
final private Peer peer;
	
	public ExampleSimple(int peerId) throws Exception{
		
		/*new  PeerMaker(Number160.createHash(peerId))����peerIdf������һ��160bit������Ϊ���peer��ID��
		setEnableIndirectReplication(true)�������������
		���ֵĬ����false�ģ����������������(A,B,C,D,E),A�˷�������ݹ��̣�����B,C,D������ģ��������ݴ�����B,C,D�ˣ�
		֮���¼��������㣺F,G,H���պ�����������ID�ܽ��������ʱ��һ���ڵ㷢���ѯ����鵽F,G,H�ϣ���ʧ�ܵġ�
		������õĺ����ǣ���̽�⵽������peerʱ�����ݻ�Ʈ��F,G,H�ϣ������Ϳ��Բ鵽�ˡ�
		*/
		peer =new  PeerMaker(Number160.createHash(peerId)).setPorts(4000+peerId).setEnableIndirectReplication(true).makeAndListen();
		FutureBootstrap fb =peer.bootstrap().setBroadcast().setPorts(4001).start();
		//fb.await();
		fb.awaitUninterruptibly();
		if(fb.getBootstrapTo()!=null){
			peer.discover().setPeerAddress(fb.getBootstrapTo().iterator().next()).start().awaitUninterruptibly();
		}
	}
	public static void main(String [] args) throws NumberFormatException,Exception{
		ExampleSimple dns  = new ExampleSimple(Integer.parseInt(args[0]));
		if(args.length==3){
			dns.store(args[1],args[2]);
		}
		
		if(args.length==2){
			System.out.println("Name: "+args[1]+" IP:"+dns.get(args[1])+"----------------------------------------------");
		}
		
	}
	private String get(String name) throws ClassNotFoundException,IOException{
		FutureDHT futureDHT = peer.get(Number160.createHash(name)).setAll().start();
		futureDHT.awaitUninterruptibly();
		if(futureDHT.isSuccess()){
			Map<Number160, Data> result=futureDHT.getDataMap();//�õ����Ǹ�locationkey�µ��������ݡ�
			for(Map.Entry<Number160,Data> entry:result.entrySet()){
				System.out.println(entry.getValue().getObject());
			}
			return futureDHT.getData().getObject().toString();//������ص��ǵ�һ���ĵ�����
		}
		return "not found";
	}
	
	private void store(String name,String ip) throws IOException{
		peer.put(Number160.createHash(name)).setData(new Number160(11),new Data(ip)).start().awaitUninterruptibly();
		Long x=4574412L;
		peer.put(Number160.createHash(name)).setData(new Number160(x),new Data(ip+"fuck")).start().awaitUninterruptibly();
		
	}

}
