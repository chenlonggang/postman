
/*
 * 文件结构如下：
 * filename_data是数据，在下载过程中，这个数据可能是“坑坑洼洼”的
 * filename_index是记录那些chunk已成功下载的文件。第一个域记录chunk总数
 * 后面记录每次成功下载的chunk的id。
 */
/*
 * chunk传输格式如下：statues+data+CRC32
 * statues：表示服务端有没有找到数据，即statues后面还有没有数据。
 *          如果statues为0，则表示，后面还有数据。
 *          如果statues为1，则表示，后面没有数据了。
 *          一个byte
 * data：文件数据,
 * CRC32：data域的CRC32校验值。
 *        32位16进制的字符串格式，8bytes。
 *        
 *  需要改动的类：DownloadThread和DownLoadService中的方法。
 */
package org.chen.p2p;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;

import net.tomp2p.futures.FutureDHT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

public class DownloadThread implements Callable<Integer> {
	List<ChunkHolderList> lists = null;
	private Peer peer=null;
	private int port;//peer在这个端口上工作
	private String filename; //文件名
	private String path;//工作路径
	private Object lock;//锁对象
	private DownLoadState state;//下载状态对象 
	
	
	/*下载该chunk，用socket，该链接谁，chunk里面有，注意port要加1，
	chunk里面的port是DHTPeer之间交互的poer
	提供下载服务的port，默认是这个port的加1
	下载有两种状态，成了，失败了。ok，failed。
	尝试下载，用socket，设定最长的忍受时间
	如果下载成功，写入filename_data和filename_index文件，braek
	*/

	private void getAbdSaveData1(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
	
		byte[] buf = new byte[2048];
		int num = in.read(buf);
		//System.out.println(num);
		long offset = datafile.length();
		datafile.seek(offset);//文件指针移到文件末尾
		while (num != (-1)) {
			//System.out.println(num);
			datafile.write(buf, 0, num);
			offset=offset+num;
			datafile.seek(offset);
			num=in.read(buf);
		}
		//data数据写入完毕
		//开始写入indexfile
		indexfile.writeLong(chunk.getChunk_id());
		indexfile.writeLong(offset);
		indexfile.writeLong(chunk.getThisChunkSize());
		datafile.close();
		indexfile.close();
	}
	/*
	 * 写入逻辑要改，旧的方案中，最终的数据文件filename_data是需要结合filename_index文件来合并的。
	 * 这样的话，提供下载服务时，获取数据给别人时就有两个逻辑：一个处理数据在完整的数据中，一个处理数据在filename_data
	 * 中获取数据的情况。
	 * 现在这样干：用RandomAccessFile的seek直接跳到对应的位置，直接写入正确的位置就可以了
	 * 这样干之后的效果：提供下载服务时，获取数据的逻辑只有一个，就是seek到对应位置，然后读数据，简化了合并操作也简化了提供数据的逻辑
	 * 皆大欢喜。呵呵
	 * 
	 * 带来的问题是：旧方案中，_index和_inddex文件写入数据时，一直是末尾追加的方式，磁头移动的代价较小
	 * 但是新的问题是：用RandomAccessFile后，数据写入的过程中磁头可能会前前后后的移动，最终的写入性能肯定没有
	 * 末尾追加的方式好，各有利弊吧。
	 */
	private void getAndSaveData2(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
		//一般情况下，normalchunksize==thischunksize,最后一个快的时候不一样。
		long normalchunksize= chunk.getNormalChunksize();//得到块大小。
		long thischunksize  = chunk.getThisChunkSize();//得到这个快的大小，
		long id=chunk.getChunk_id();//得到这个chunk的id.
		byte[] buf = new byte[2048];
		int num= in.read(buf);
		long offset = normalchunksize*id;//得到这个快的偏移量。
		datafile.seek(offset);
		while(num!=(-1)){
			datafile.write(buf,0,num);
			offset=offset+num;
			datafile.seek(offset);
			num=in.read(buf);
		}
		//data文件写入完毕
		//开始写入indexfile数据，写入chunkid,记得将indexfile的写指针的位置后移到文件末尾。
		indexfile.seek(indexfile.length());
		indexfile.writeLong(id);
		datafile.close();
		indexfile.close();
	}
	private void getAndSaveData4(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
		long normalchunksize = chunk.getNormalChunksize();
		long size = chunk.getThisChunkSize();
		long id =chunk.getChunk_id();
		byte [] buf = new byte[1024*8];
		long offset=normalchunksize*id;
		CRC32 crc32 = new CRC32();
		long crcnow=0;
		long bytes=0;
		int overloop=0;
		int num = in.read(buf);
		while(num!=(-1)){
			if(size-bytes >= num){
				writedata(datafile,offset,buf,num);
				offset=offset+num;
				bytes=bytes+num;
				crc32.update(buf, 0, num);
				if(bytes==size)
					break;
					
				num=in.read(buf);
			}else{
				writedata(datafile,offset,buf,(int)(size-bytes));
				offset=offset+size-bytes;
				overloop=(int) (num-(size-bytes));
				 /*
				  * 这个uodate一定要放到bytes=size的前面，不然不对呀
				  * 坑爹呀，找了4个小时.
				  */
				crc32.update(buf, 0, (int)(size-bytes));
				bytes =size;
				break;
			}
		}
		byte [] crcbyte =new byte[4];
		int i=0;
		for(i=0;i<overloop;i++){
			crcbyte[i] = buf[ (num-overloop+i)];
		}
		for(int j=0;j<4-overloop;j++,i++){
			crcbyte[i]=(byte) in.read();
		}
		for(i=3;i>=0;i--){
			crcnow = crcnow+((((long)crcbyte[3-i])&0xff)<<(i*8));
		}
		if(crc32.getValue()==crcnow){
			//data文件写入完成，开始写入index文件。
			writeindex(indexfile,id);
		}
		else{
			/*
			 * 如果CRC32校验失败，输出提示，抛出异常，downloadchunk会捕获并处理.
			 */
			System.out.println(chunk.getChunk_id()+" "+Long.toHexString(crcnow).toUpperCase()+" "+Long.toHexString(crc32.getValue()).toUpperCase());
			throw new IOException("socket error: crc32 change!");
		}
		try{
			//写完之后，关闭文件句柄
			datafile.close();
			indexfile.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	private void getAndSaveData3(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
		long normalchunksize = chunk.getNormalChunksize();
		long id =chunk.getChunk_id();
		byte [] buf = new byte[1024*8];
		int num = in.read(buf);
		long offset=normalchunksize*id;
		while(num!=(-1)){
			writedata(datafile,offset,buf,num);
			offset=offset+num;
			num=in.read(buf);
		}
		//data文件写入完成，开始写入index文件。
		writeindex(indexfile,id);
		//写完之后，关闭文件句柄
		datafile.close();
		indexfile.close();
	}
	
	private void writedata(RandomAccessFile data,long offset,byte [] buf,int num) throws IOException{
		synchronized(lock){
			data.seek(offset);
			data.write(buf,0,num);
		}
	}
	private void writeindex(RandomAccessFile index,long id) throws IOException{
		synchronized(lock){
			index.seek(index.length());
			index.writeLong(id);
		}
	}
	/*index表示要从address哪里获取该chunk的数据，peer的默认DownService端口为peer工作端口+1.
	 * 这个函数的主逻辑有问题，不应该先建立socket链接，在判断要不要下载。如果判断是不能下载的，服务端socket发的数据无人接收，
	 * 会异常的.
	 * 新逻辑：先判断要不要继续下载，再发socket。
	 * 好处是：那个异常可以没了，而且尽量现在客户端判断要不要建立socket链接，这样就可以减少一些不必要的链接，
	 * socket链接的代价也不小，能省就省
	 */
	/*
	 * 2014.8.5:
	 * 这里的判断逻辑提高都DHTPeer里面去做，这个类只需要转型负责下载、写数据就可以了。
	 * 写数据用的文件也已经由DHTPeer的DownLoader类的download方法创建好了。
	 * 执行到这里的时候，一切准备工作都已经实现准备妥当，直接写数据就可以了。
	 */
	public String DownloadChunk2(ChunkHolderList chunk,String address){
		String [] parts= address.split(":");
		String ip=parts[0];
		int port = Integer.parseInt(parts[1])+1;
		
		GetChunk command = new GetChunk(chunk.getFilename(),chunk.getChunk_num(),chunk.getChunk_id(),chunk.getNormalChunksize(),chunk.getThisChunkSize());
		//和服务器建立socket链接。
		Socket socket=null;
		ObjectOutputStream out=null;
		InputStream netIn =null; 
		InputStream in=null;
		String result="failed";
		/* 
		 *锁的范围太大了，这样实质上其实就是单线程的 
		 */
		synchronized(lock){
			//String data=path+File.separator+filename+"_data";
			String data =path+File.separator+filename+Suffix.datafilesuffix;
			//String index=path+File.separator+filename+"_index";
			String index = path+File.separator+filename+Suffix.indexfilesuffix;
			RandomAccessFile datafile=null;
			RandomAccessFile indexfile=null;
			File dfile = new File(data);
			File ifile = new File(index);
			try{
				datafile = new RandomAccessFile(dfile,"rw");
				indexfile= new RandomAccessFile(ifile,"rw");
				//现在建立socket链接，发送请求。
				socket = new Socket(ip, port);
				out= new ObjectOutputStream(socket.getOutputStream());
				out.writeObject(command);
				out.flush();
				netIn=socket.getInputStream();
				in = new DataInputStream(new BufferedInputStream(netIn));
				int statues =in.read();
				if(statues==0){
					//System.out.println("data is fetched");
					getAndSaveData2( in, datafile, indexfile, chunk);
					result ="ok";
				}
				else{
					result="failed";
				}
				
			}catch(Exception e){//任何的异常，都将导致该次下载尝试失败。
				e.printStackTrace();
				result = "failed";
			}finally{
				try {
					if(out!=null)out.close();
					if(in!=null)in.close();
					out=null;
					in=null;
					if(socket!=null)socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		return result;
	}
	
	/*
	 * 试图减小锁的范围，让并行真的并行
	 */
	public String DownloadChunk3(ChunkHolderList chunk,String address){
		String [] parts = address.split(":");
		String ip =parts[0];
		int port = Integer.parseInt(parts[1])+1;
		GetChunk command = new GetChunk(chunk.getFilename(),chunk.getChunk_num(),chunk.getChunk_id(),chunk.getNormalChunksize(),chunk.getThisChunkSize());
		Socket socket =null;
		ObjectOutputStream out=null;
		InputStream netIn=null;
		InputStream in=null;
		String result="failed";
		
		//String data= path+File.separator+filename+"_data";
		//String index=path+File.separator+filename+"_index";
		String data=path+File.separator+filename+Suffix.datafilesuffix;
		String index=path+File.separator+filename+Suffix.indexfilesuffix;
		RandomAccessFile datafile=null;
		RandomAccessFile indexfile=null;
		File dfile=new File(data);
		File ifile=new File(index);
		try{
			datafile = new RandomAccessFile(dfile,"rw");
			indexfile= new RandomAccessFile(ifile,"rw");
			//建立socket链接，发送请求
	//		System.out.println(ip+": "+port);
			socket = new Socket(ip,port);
			socket.setSoTimeout(50000);
			/*
			 * 读操作最多等待两秒,如果超过了这个时间，任然没有数据，read会抛出一个SocketTiemoutException,
			 * 程序需要捕获这个异常，但是当前的socket链接依然是有效的。如果对方进程奔溃，宕机或者网络断开，
			 * 本地的read会一直阻塞下去，此时设置超时时间就非常重要，否则read的线程会一直挂起。
			 */
			out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(command);
			out.flush();
			netIn = socket.getInputStream();
			in=new DataInputStream(new BufferedInputStream(netIn));
			int statues= in.read();
			if(statues==0){
				getAndSaveData4(in,datafile,indexfile,chunk);
				result ="ok";
			}
			else{
				result = "failed";
			}
		}catch(Exception e){
			e.printStackTrace();
			result="failed";
		}finally{
			try{
				if(out!=null)out.close();
				if(in!=null) in.close();
				out=null;
				in=null;
				if(socket!=null) socket.close();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		//System.out.println(result);
		return result;
	}
	/*
	 * 下载chunk分片，地址是address。
	 */
	public String DownloadChunk(ChunkHolderList chunk,String address){
		String [] parts= address.split(":");
		String ip=parts[0];
		int port = Integer.parseInt(parts[1])+1;
		//System.out.println("ip："+parts[0]+" port: "+port);
		//包装好get命令。
		GetChunk command = new GetChunk(chunk.getFilename(),chunk.getChunk_num(),chunk.getChunk_id(),chunk.getNormalChunksize(),chunk.getThisChunkSize());
		//和服务器建立socket链接。
		Socket socket=null;
		ObjectOutputStream out=null;
		InputStream netIn =null; 
		InputStream in =null;
		String result="failed";
		try {
			socket = new Socket(ip, port);
			out= new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(command);
			out.flush();
			
			netIn=socket.getInputStream();
			in = new DataInputStream(new BufferedInputStream(netIn));
			int statues =in.read();
			if(statues==0){
				System.out.println("data is fetched");
				
				//String filename = chunk.getFilename();
				//现在需要写文件了，此时需要加锁。
				synchronized(lock){
					//第一步，判断filename_data和filename_index文件是否存在，如果不从在，创建。
					//第二部，写入数据。
					//String data=path+File.separator+filename+"_data";
					//String index=path+File.separator+filename+"_index";
					String data=path+File.separator+filename+Suffix.datafilesuffix;
					String index=path+File.separator+filename+Suffix.indexfilesuffix;
					File dfile = new File(data);
					File ifile = new File(index);
		
					//只有这两个文件都从在，才认为”存在“,如果只有一个或都没有，那是错的
					if(dfile.exists() && ifile.exists()){//都在。直接存
						
						RandomAccessFile datafile = new RandomAccessFile(dfile,"rw");
						RandomAccessFile indexfile= new RandomAccessFile(ifile,"rw");
						getAndSaveData2( in, datafile, indexfile, chunk);
						result ="ok";
					}
					else if(!dfile.exists() && !ifile.exists()){//都不在，先建立文件，再存
						ifile.createNewFile();//创建一个存数据索引的文件
						dfile.createNewFile();//创建一个存放数据的文件
						RandomAccessFile datafile = new RandomAccessFile(dfile,"rw");
						RandomAccessFile indexfile= new RandomAccessFile(ifile,"rw");
						//indexfile的第一个值表示有几个该文件有几个chunk，随后是成功下载的chunkid，
						
						//新建时，先写入chunknum，设置写指针偏移量。
						indexfile.writeLong(chunk.getChunk_num()); 
						indexfile.seek(8);
						getAndSaveData2( in, datafile, indexfile, chunk);
						result ="ok";
					}
					else{//只有一个在，fuckaway
						System.out.println("[*_index和*_data是配对出现的，删除单独出现的*_index或*_data文件，重新开始]");
						result="failed";
					}
				}
				
			}
			else{//没有找到数据。
				result="failed";
			}
			
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			result = "failed";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			result = "failed";
		}finally{
			 try {
				out.close();
				in.close();
				out=null;
				in=null;
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		return result;
	}
	public DownloadThread(List<ChunkHolderList> sublists,Peer peer,int port,String filename,String path,Object lock,DownLoadState state){
		this.lists=sublists;
		this.peer=peer;
		this.port=port;
		this.filename=filename;
		this.path=path;
		this.lock=lock;
		this.state=state;
	}
	
	public Integer call(){
		int successed = 0;
		//顺序下载该线程负责的所有分片，返回成功下载，flush的chunk数目。
		for(ChunkHolderList chunk:lists){//下载chunk分片
			//List<String> oweners = chunk.getIp_pors();//得到拥有chunk分片的机器列表。
			List<String> oweners=chunk.RefreshIp_Ports(this.peer); //实时模式，可以降低热点。
			String result="failed";
			
			if(oweners!=null && oweners.size()>0){ //当得到的oweners不为null，并且里面有东西时，开始下载，否则，不下载。
				int times=oweners.size()>3?3:oweners.size();//每个chunk最多尝试3次，如果都失败，则放弃
				
				Random rdm = new Random(System.currentTimeMillis()); //以当前时间为种子，生成随机数
			
				while(times > 0){
					//如果不成功，尝试下一次
					int index = Math.abs(rdm.nextInt())%oweners.size();//生成的index的范围为[0...oweners-1]，
					result = DownloadChunk3(chunk,oweners.get(index));//ip:port格式
					if(result=="ok"){
						//System.out.println(chunk.getChunk_id()+" is download ok");
						break;
					}
					else
						times--;
				}
			}
			//当尝试3次以上或者，oweners里面没有东西时，result=failed，失败了。
			//若果下载成功，成功数加1,并将自己添加到DHT网络中,
			if(result=="ok"){
				successed=successed+1;
				if(state!=null){
					synchronized(lock){
						state.IncreasePercent(1.0/state.getChunknum());
					}
				}
			
				try{
					
					InetAddress addr = InetAddress.getLocalHost();
					String ip=addr.getHostAddress().toString()+":"+port;//获得本机IP:port,作为进程唯一标示。
					peer.put(Number160.createHash(filename+"_"+chunk.getChunk_id())).setData(Number160.createHash(ip), new Data(ip)).start().awaitUninterruptibly();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		return successed;
	}
}
