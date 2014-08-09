// 

package org.chen.p2p;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureDHT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;


/*
 * 一个DHTPeer即是客户端又是服务器端
 * 有时间的话，这个类需要重构一把
 */
public class DHTPeer extends Observable{
	int port=-1;
	int serveport=-1;//作为服务，供其他人下载数据的端口
	int downloadthreads=20;
	final private Peer peer;
	String currentpath=null;
	String getCurrentPath(){
		return currentpath;
	}
	public DHTPeer(int peerid) throws Exception{
		super();
		//System.out.println(peerid);
		port=5000+peerid;
		serveport=port+1;
		peer =new  PeerMaker(Number160.createHash(peerid)).setPorts(port).setEnableIndirectReplication(true).makeAndListen();
		FutureBootstrap fb =peer.bootstrap().setBroadcast().setPorts(5001).start();
		fb.awaitUninterruptibly();
		if(fb.getBootstrapTo()!=null){
			peer.discover().setPeerAddress(fb.getBootstrapTo().iterator().next()).start().awaitUninterruptibly();
		}
		currentpath = new File("../").getAbsolutePath()+File.separator+"data";
		File file = new File(currentpath);
		if(!file.exists())
			file.mkdir();
	}
	

	public void shutDown(){
		if(peer!=null)
			peer.shutdown();
	}

	public void StartDownLoadService(){
		DownLoadService download = new DownLoadService(serveport,currentpath);
		Thread thread=new Thread(download);
		thread.setDaemon(true); //把服务线程设为后台线程。
		thread.start();
	}
	/*
	 * 和共享文件相关的两个函数
	 * sharemetadata：共享文件的元信息到DHT网络，包括文件的大小，chunk的大小等信息。
	 * share：文件共享，先调用sharemetadata共享元信息，再共享chunk信息，比如：谁有，第几个等。
	 * ------
	 * *这个目前看不到做成多线程的必要性，一是：共享速度较快，
	 * *二是：很少有人批量的启动共享程序。
	 */
	private String sharemetadata(String filename) throws ClassNotFoundException, IOException, InterruptedException{
		//共享一个文件的元信息到DHT网络中。
		//System.out.println(filename);
		FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
		futureDHT.awaitUninterruptibly();
		//在DHT中查到了该文件的元信息。
		if(futureDHT.isSuccess()){
			System.out.println("网络中已有该文件的原始信息");
			FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//从网络中获取该元数据。
			//元信息被损坏了，或者是不正确的。
			if(meta.chunkNum()==-1){
				//损坏了或不正确，删掉。
				FutureDHT re=peer.remove(Number160.createHash(filename)).start();
				re.await();
				if(re.isSuccess()){
					//添加元信息
					sharemetadata(filename);
				}
				else{
					//能查到，删不掉，你是有多贱。
					return "failed";
				}
			}
			else return "failed";
		}
		//DHT网络中没有该文件的元信息，则添加。
		else{
			System.out.println("网络中还没有这个文件的原始信息");
			File f = new File(currentpath+File.separator+filename);
			if(f.isFile() && f.exists()){
				FutureDHT re=peer.put(Number160.createHash(filename)).setData(new Data(new FileMetadata(filename,currentpath))).start();
				re.awaitUninterruptibly();
				if(re.isSuccess()){
					return "ok";
				}
				else return "failed";
			}
			else{
				System.out.println("文件不存在");
				return "failed";
			}
		}
		return "ok";
	}
	public void  share(String filename) throws IOException, ClassNotFoundException, InterruptedException{
		//分享文件持有信息到DHT网络.先把元信息共享，再是片信息。
		String result = sharemetadata(filename);
		System.out.println("share metadata "+result);
		if(result.equals("ok")){
			FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
			futureDHT.awaitUninterruptibly();
			if(futureDHT.isSuccess()){
				FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//从网络中获取该元数据。
				InetAddress addr = InetAddress.getLocalHost();
				String ip=addr.getHostAddress().toString()+":"+port;//获得本机IP:port,作为进程唯一标示。
				for(int i=0;i<meta.chunkNum();i++){
					peer.put(Number160.createHash(filename+"_"+i)).setData(Number160.createHash(ip), new Data(ip)).start().awaitUninterruptibly();
				}
			}
			else{
				System.out.println("获取不到元信息");
			}
		}
		else{
			System.out.println("元数据分享失败");
		}
		//System.out.println("share chunk list ok");
	}
	

	
	/*
	 * 两个批量下载的方法，一个有监听器，一个没有
	 */
	public List<Future<String>>BatchDownLoad(List<String> filenames){
		return BatchDownLoad(filenames,null);
	}
	public List<Future<String>>BatchDownLoad(List<String> filenames,List<DownLoadState> states ){
		//批量下载好多文件
		if(filenames!=null && filenames.size()>0){
			List<Future<String>> futures = new ArrayList<Future<String>>();
			ExecutorService threadPool = Executors.newCachedThreadPool();
			/*
			 * for循环内部需要和Download内部差不多的过程，来判断下载任务的类型。
			 */
			for(int i=0;i<filenames.size();i++){
				
				if(filenames.get(i)!=null){
					DownLoadState state=null;
					if(states!=null){
						state = new DownLoadState();
						states.add(state);
					}
					File indexfile = new File(currentpath+File.separator+filenames.get(i)+Suffix.indexfilesuffix);
					File datafile  = new File(currentpath+File.separator+filenames.get(i)+Suffix.datafilesuffix);
					if(!indexfile.exists() && !datafile.exists()){//新的下载任务，崭新的。
						futures.add(threadPool.submit(new DownLoader(filenames.get(i),state)));
					}
					else if(indexfile.exists() && datafile.exists()){//如果都在，则继续下载未完成的任务
						futures.add(threadPool.submit(new DownloaderContinue(filenames.get(i),state)));
					}
					else{//这个下载任务有问题：报错
						futures.add(threadPool.submit(new DownloaderCrash(state)));
					}
				}
			}
			threadPool.shutdown();//等待池中的所有的线程完成后，关闭线程池
			return futures;
		}
		else
			return null;
	}
	/*
	 * 两个单独下载某个文件的方法，一个有监听器，一个没有.
	 */
	public Future<String> DownLoad(String filename,DownLoadState state){
		/*需要在这个地方，判断一个下载的类型。
		 *是新鲜启动的任务，Downloader类处理
		 *还是继续完成之前未完成的任务。 DownloaderContinue类处理
		 *还是要报错。DownLoadError类处理
		 */
		if(filename!=null){
			File indexfile=new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			File datafile =new File(currentpath+File.separator+filename+Suffix.datafilesuffix);
			if(!indexfile.exists() && !datafile.exists() ){
				//如果这两个文件不存在，则认为这是一个崭新的下载认为
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownLoader(filename,state));
				threadPool.shutdown();//等待池中所有线程都完成后，关闭线程池。
				return future;
			}
			else if(indexfile.exists() && datafile.exists()){
				//如果都在，则认为是继续下载以前没有完成的任务
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownloaderContinue(filename,state));
				threadPool.shutdown();//等待池中所有线程都完成后，关闭线程池。
				return future;
			}
			else{
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownloaderCrash(state));
				threadPool.shutdown();//等待池中所有线程都完成后，关闭线程池。
				return future;
			}
		}
		else
			return null;
	}
	public Future<String> DownLoad(String filename){
		return DownLoad(filename,null);
	}
	
	private class DownloaderCrash extends DownLoader{
		/*
		 * 处理错误下载请求的类：
		 * 下载请求错误的类型：
		 * a:源文件存在，还要求下载
		 * b:数据和index文件中的一个被人误删了。
		 * 总之数据和index少一个。
		 * 提示：源文件存在或零时文件配破坏。--》crash
		 */
		DownloaderCrash(DownLoadState state){
			super(null, state);
			
		}
		@Override
		public String call() throws Exception {
			// TODO Auto-generated method stub
			if(state!=null){
				state.setIsdone(true);//完成了
				state.setIsok(false);//但是结果是错的
				state.setPercent(0.0);//完成率
			}
			return "crash";
		}
		
	}
	
	private class DownLoader implements Callable<String>{
		/*
		 * 默认的实现认为：下载是新鲜的，即新开启的一个下载任务。
		 * 内部类，负责下载一个文件,负责下载一个新的文件。
		*/
		protected DownLoadState state=null;
		protected String filename=null;
		public DownLoader(String filename,DownLoadState state){
			this.filename=filename;
			this.state=state;
		}
		@Override
		public String call() throws Exception {
			// TODO Auto-generated method stub
			//这里能根据不同的的情况，选择不同的策略。
		/*	File indexfile=new File(currentpath+File.separator+filename+"_index");
			if(indexfile.exists()){//filename_index文件存在，需要从磁盘获取下载任务.
				
			}
		*/
			return download(filename);
		}
		 /* 和下载相关的四个函数，这里的getFileMetaDataFromDHT、getchunkslistFromDHT、downloadFresh
		 * 是假设下载时崭新的。
		 * getFileMetaDataFromDHT:从DHT网络中得到文件的元信息，即得到文件大小，文件chunk大小，chunk数量。
		 * getchunkslist:从DHT网络中得到每个chunk的拥有者列表。
		 * downloadchunks:下载所有的chunk。分成20个线程，每个线程负责处理一部分。
		 * download：下载函数，下载文件filename。
		 * --------
		 * *现在想把这几个方法放到一个内部类中，这个内部类实现Callable接口，这样的话，
		 * *一个下载任务就对应一个线程，可以多线程下载。
		 * --------
		 * *用内部类的理由是：下载时需要peer，currentpath等这些量，不想当做参数传来传去。
		 * *而内部类可以访问外部类的成员，所以就这么干吧
		 */
		private FileMetadata getFileMetaData(String filename ) throws ClassNotFoundException, IOException{
			//从DHT网络中得到某个文件的meta信息
			FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
			futureDHT.awaitUninterruptibly();
			if(futureDHT.isSuccess()){
				FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//从网络中获取该元数据。
				return meta;
			}
			else
				return null;
		}
		private List<ChunkHolderList> getchunkslist(String filename) throws ClassNotFoundException, IOException{
			List<ChunkHolderList> lists = new ArrayList<ChunkHolderList>();
			//尝试获取元信息。
			FutureDHT futureDHT = peer.get(Number160.createHash(filename)).start();
			futureDHT.awaitUninterruptibly();
			FileMetadata meta=null;
			System.out.println("get meta data...");
			if(futureDHT.isSuccess()){
				meta=(FileMetadata)futureDHT.getData().getObject();
				//System.out.println(meta.chunkNum());
				//System.out.println(meta.lastChunkSize());
				//System.out.println(futureDHT.getData().getObject().toString());
						//return futureDHT.getData().getObject().toString();
			}
			else{
				return lists;//如果得不到元信息，返回空列表
			}
			//尝试获取chunk列表信息。
			long chunknum = meta.chunkNum();
			FutureDHT chunkDHT=null;
					
			//第i个chunk的ip:port格式的信息
			System.out.println("get chunk list ...");
			
			for(int i=0;i<chunknum;i++){
				chunkDHT=peer.get(Number160.createHash(filename+"_"+i)).setAll().start();
				chunkDHT.awaitUninterruptibly();
				//chunkDHT.getData().getObject():这样得到的是指向第一个数据项的迭代器,
						
				/*//只输出第一个值
				if(chunkDHT.isSuccess()){
					System.out.println(chunkDHT.getData().getObject().toString());
				}
				*/
						
				//下面输出所有的值,并且填充该chunk的 ChunkHolderList。
						
				if(chunkDHT.isSuccess()){
					ChunkHolderList list = new ChunkHolderList();
					//if(i==chunknum-1)
					//	list.setchunkSize(meta.lastChunkSize());
					//else
					//	list.setchunkSize(meta.chunkSize());
					list.setNormalChunkSize(meta.chunkSize());
					if(i==chunknum-1)
						list.setThisChunkSize(meta.lastChunkSize());
					else
						list.setThisChunkSize(meta.chunkSize());
					list.setChunk_id(i);
					list.setChunk_num(chunknum);
					list.setFilename(filename);
					Map<Number160,Data> results = chunkDHT.getDataMap();
					for(Map.Entry<Number160, Data> entry:results.entrySet()){
						//System.out.println(entry.getValue().getObject().toString());
						list.getIp_pors().add(entry.getValue().getObject().toString());
					}
					lists.add(list);
				}
				else{
					System.out.println("该chunk暂时不可取");
				}
			}
			//System.out.println("get chunk list done");
			return lists;
		}
		protected int downloadchunks(List<ChunkHolderList>lists,int allchunknum,String filename) throws InterruptedException, ExecutionException{
			/*规则定义如下：
			 * 允许同时开启20个线程并行下载，每个线程负责下载具体的一个chunk，这一批下载结束后，开启下一批。
			 * 也可以这样，兴许效率会高些：还是20个线程，每个线程负责总chunk的1/20,每个线程挨个下载它负责的
			 * 所有chunk，这20个线程会返回执行结果，即成功下载的chunks数目，这20个线程会同步的写filename_data
			 * 和filename_index文件。
			 * 
			 * 当allchunknum==lists.size,并且每个线程都成功下载了它负责的所有chunk后，需要合并filename_data文件，此时
			 * 需要 filename_index的帮助，合并完成后，删除filename_data和file_index文件，多个线程异步的下载数据，但是
			 * 写入到filename_data和filename_index文件的时候需要同步，需要锁，需要两个文件同步写入。第二点，该点在提供下载服务的同时，还提供下载
			 * 服务，下载服务需要读取filename_data和filename_index数据，是只读的，不需要加锁。
			 * 
			 */
			Object lock = new Object();//锁对象，用来同步filename_data和filename_index的写入。
			int chunknum=lists.size();
			int chunksperthread = chunknum/downloadthreads+1;//每个线程负责的chunk的数目。
			//分发任务，发给20个线程。
			ExecutorService service = Executors.newFixedThreadPool(20);
			List<Future<Integer>> result = new ArrayList<Future<Integer>>();//记录下载结果
			for(int i=0;i<chunknum;i=i+chunksperthread){
				DownloadThread thread=null;
				if(i+chunksperthread < chunknum )
					thread =new DownloadThread(lists.subList(i, i+chunksperthread),peer,port,filename,currentpath,lock,state);
				else
					thread =new DownloadThread(lists.subList(i, chunknum),peer,port,filename,currentpath,lock,state);
				Future<Integer> future=service.submit(thread);
				result.add(future);
			}
			service.shutdown();//等待20个线程都结束，
			
			int num=0;
			for(Future<Integer> f:result)
				num+=f.get();
			return num; //返回成功下载的chunk数目。
		}
		private String download(String filename) throws ClassNotFoundException, IOException, InterruptedException, ExecutionException{
			System.out.println("download...");
			FileMetadata meta = getFileMetaData(filename);
			
			if(meta==null){ //网络中找不到这个文件的相关信息,直接返回失败信息
				if(state!=null){
					state.setIsdone(true);//已经完成。
					state.setIsok(false);//但是结果是错的
					state.setPercent(0.0);//设置完成率。
					state.setChunknum(0);
				}
				return "download is failed";
			}
			if(state!=null){
				state.setIsdone(false);
				state.setIsok(false);
				state.setFilename(filename);//设置文件名
				state.setPercent(0.0);//新开始的任务
				state.setChunknum(meta.chunkNum());//需要下载的chunk数目
			}
			
			/*
			 * 这里需要加上创建data和index文件，然后把meta存入index的代码。
			 * 这样DownThread就不用处理间文件的逻辑了，只要写就可以了。
			 */
			File datafile = new File(currentpath+File.separator+filename+Suffix.datafilesuffix);
			datafile.createNewFile();
			
			File indexfile = new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			indexfile.createNewFile();
			RandomAccessFile ifile= new RandomAccessFile(indexfile,"rw");
			ifile.writeLong(meta.fileLength());//写入文件大小
			ifile.seek(8);
			ifile.writeLong(meta.chunkSize());//写入chunk大小 
			ifile.seek(16);
			ifile.writeLong(meta.lastChunkSize());//写入最后一个快的大小
			ifile.seek(24);
			ifile.writeLong(meta.chunkNum());//得到chunk数目.
			ifile.close();
			
			
			int allchunknum =(int) meta.chunkNum(); //总共有这个多chunk
			
			List<ChunkHolderList>lists=getchunkslist(filename);
			int availablechunknum = lists.size(); //有这个多的chunk的信息是可取的。
			
			int downloaded=downloadchunks(lists,allchunknum,filename); //成功下载了这么多的chunk。
			System.out.println("总共 ："+allchunknum);
			System.out.println("共 :"+availablechunknum+"可取");
			System.out.println("成功下载: "+downloaded);
			if(allchunknum==downloaded){//成功下载了所有的分片，成功了。
				//删除filename_index文件
				//File indexfile=new File(currentpath+File.separator+filename+"_index");
				if(indexfile.isFile() && indexfile.exists()){
					boolean success=indexfile.delete();
					if(success){
		//				System.out.println("delete index file success");
					}
		//			System.out.println("all chunks are downloaded successfully");
				}
				if(state!=null){
					state.setIsdone(true); //完成了
					state.setIsok(true);   //正确的完成了
					state.setPercent(100.0); //设置完成率
				}
				return "download is ok ";
			}
			else{
				if(state!=null){
					state.setIsdone(true);//完成了
					state.setIsok(false); //但是结果是错误的，即下载出问题了
				}
				return "download is failed";
			}
			
		}

	}
	
	private class DownloaderContinue extends DownLoader{
		/*
		 * 这个类负责实现下载“半拉子工程”.
		 * 需要从本地的缓存文件获取相关信息。
		 * 
		 */
		public DownloaderContinue(String filename,DownLoadState state) {
			super(filename,state); 
			// TODO Auto-generated constructor stub
		}
		@Override
		public String call() throws Exception {
			return download(filename);
			
		}
		private FileMetadata getFileMetaData(String filename ){
			File indexfile = new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			RandomAccessFile ifile=null;
			try {
				ifile = new RandomAccessFile(indexfile,"rw");
				long filelength = ifile.readLong();
				ifile.seek(8);
				long chunksize =  ifile.readLong();
				ifile.seek(16);
				long lastchunksize = ifile.readLong();
				ifile.seek(24);
				long chunknum = ifile.readLong();
				ifile.close();
				FileMetadata meta = new FileMetadata(filelength,lastchunksize,chunknum);
				if(chunksize!=meta.chunkSize()){
					System.out.println("index file is broken");
					return null;
				}
				return meta;
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
		private List<ChunkHolderList> getchunkslist(String filename) throws IOException{
			//这里需要填充一个ChunkHolderlist的列表，ip_pors分量可以先不填充
		/*	private List<String> ip_pors;  //拥有该chunk的机器列表
			private String filename;       //文件名字
			private long chunk_num;        //该文件的chunk数目
			private long chunk_id;         //这个chunk的编号
			private long normalchunk_size; //其他chunk的大小。
			private long thischunk_size;   //这个chunk的大
		*/
			List<ChunkHolderList> lists = new ArrayList<ChunkHolderList>();
			FileMetadata meta = getFileMetaData(filename);
			if(meta==null)
				return lists;
			File indexfile = new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			RandomAccessFile ifile = new RandomAccessFile(indexfile,"rw");
			//ifile.seek(32);
			long bytes = indexfile.length();
			long chunksdownloadednums = (bytes-32)/8;
			List<Long> all = new ArrayList<Long>();
			for(long i=0;i<meta.chunkNum();i++){
				all.add(i);
			}
			List<Long> downloaded = new ArrayList<Long>();
			long offset=32;
			for(long i=0;i<chunksdownloadednums;i++){
				ifile.seek(offset);
				downloaded.add(ifile.readLong());
				offset=offset+8;
			}
			ifile.close();//读完文件要记得关闭
			all.removeAll(downloaded);
			/*此时，all里面放的就是所有需要下载的chunk的chunkid
			 *填充ChunkHolderList，塞到lists里面就可以了。
			 */
			for(int i=0;i<all.size();i++){
				ChunkHolderList chunk = new ChunkHolderList();
				chunk.setChunk_id(all.get(i));
				chunk.setChunk_num(meta.chunkNum());
				chunk.setFilename(filename);
				chunk.setNormalChunkSize(meta.chunkSize());
				if(all.get(i)==meta.chunkNum()-1)
					chunk.setThisChunkSize(meta.lastChunkSize());
				else
					chunk.setThisChunkSize(meta.chunkSize());
				
				lists.add(chunk);
			}
			return lists;
			
		}
		private String download(String filename){
			System.out.println("download...continue...");
			FileMetadata meta = getFileMetaData(filename);
			if(meta==null){
				if(state!=null){
					state.setIsdone(true);//已经完成。
					state.setIsok(false);//但是结果是错的
					state.setPercent(0.0);//设置完成率。
					state.setChunknum(0);
				}
				return "download is failed";
			}
			
			if(state!=null){
				state.setIsdone(false);
				state.setIsok(false);
				state.setFilename(filename);//设置文件名
				state.setPercent(0.0);//新开始的任务
				state.setChunknum(meta.chunkNum());//需要下载的chunk数目
			}
			
			List<ChunkHolderList> lists=null;
			try {
				lists =getchunkslist(filename);
				if(state!=null)
					state.setChunknum(lists.size());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("error");
				e.printStackTrace();
			}
			int allchunknum=lists.size();
			int downloaded=0;
			try {
				downloaded = downloadchunks(lists,allchunknum,filename);
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("总共: "+allchunknum);
			System.out.println("成功下载："+downloaded);
			if(allchunknum==downloaded){
				/*
				 * 成功下载了所有的分片，需要删除index文件
				 */
				File indexfile = new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
				if(indexfile.isFile() && indexfile.exists()){
					boolean success = indexfile.delete();
					if(success){
						System.out.println("delete index file success");
					}
				}
				System.out.println("all chunks are downloaded successfully");
				if(state!=null){
					state.setIsdone(true);
					state.setIsok(true);
					state.setPercent(100.0);
				}
				return "download is ok ";
			}
			else{
				if(state!=null){
					state.setIsdone(true);
					state.setIsok(false);
				}
				return "download is failed";
			}
		}
	}

	public static void main(String [] args) throws NumberFormatException, Exception{
	    DHTPeer dhtpeer=null;
		try{
			dhtpeer  = new DHTPeer(Integer.parseInt(args[0]));
			//System.out.println("d");
			dhtpeer.StartDownLoadService();
			System.out.println(args[1]);
			dhtpeer.share(args[1]);
			dhtpeer.share("solr-4.7.1-src.tgz");
			dhtpeer.share("lucene-4.9.0.zip");
			DownLoadState state = new DownLoadState();
			System.out.println(dhtpeer.DownLoad(args[1]).get());
		
			List<String> filenames = new ArrayList<String>();
			filenames.add("solr-4.7.1-src.tgz");
			filenames.add("lucene-4.9.0.zip");
			List<Future<String>> result= dhtpeer.BatchDownLoad(filenames);
			for(Future<String> f:result){
				System.out.println(f.get());
			}
		
			dhtpeer.shutDown();
			
			
		}catch(Exception e){
			System.out.println("error**********************************************");
			e.printStackTrace();
		}
	}
}
