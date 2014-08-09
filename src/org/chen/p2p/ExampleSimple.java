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
		
		/*new  PeerMaker(Number160.createHash(peerId))以用peerIdf产生的一个160bit的数作为这个peer的ID。
		setEnableIndirectReplication(true)处理这种情况：
		这个值默认是false的：当：比如有五个点(A,B,C,D,E),A端发起存数据过程，发现B,C,D是最近的，于是数据存在了B,C,D端，
		之后，新加入三个点：F,G,H，刚好他们离数据ID很近，如果此时另一个节点发起查询，会查到F,G,H上，会失败的。
		这个设置的含义是：当探测到更近的peer时，数据会飘到F,G,H上，这样就可以查到了。
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
			Map<Number160, Data> result=futureDHT.getDataMap();//得到的是该locationkey下的所有数据。
			for(Map.Entry<Number160,Data> entry:result.entrySet()){
				System.out.println(entry.getValue().getObject());
			}
			return futureDHT.getData().getObject().toString();//这个返回的是第一个的迭代器
		}
		return "not found";
	}
	
	private void store(String name,String ip) throws IOException{
		peer.put(Number160.createHash(name)).setData(new Number160(11),new Data(ip)).start().awaitUninterruptibly();
		Long x=4574412L;
		peer.put(Number160.createHash(name)).setData(new Number160(x),new Data(ip+"fuck")).start().awaitUninterruptibly();
		
	}

}
