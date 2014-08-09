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
 * һ��DHTPeer���ǿͻ������Ƿ�������
 * ��ʱ��Ļ����������Ҫ�ع�һ��
 */
public class DHTPeer extends Observable{
	int port=-1;
	int serveport=-1;//��Ϊ���񣬹��������������ݵĶ˿�
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
		thread.setDaemon(true); //�ѷ����߳���Ϊ��̨�̡߳�
		thread.start();
	}
	/*
	 * �͹����ļ���ص���������
	 * sharemetadata�������ļ���Ԫ��Ϣ��DHT���磬�����ļ��Ĵ�С��chunk�Ĵ�С����Ϣ��
	 * share���ļ������ȵ���sharemetadata����Ԫ��Ϣ���ٹ���chunk��Ϣ�����磺˭�У��ڼ����ȡ�
	 * ------
	 * *���Ŀǰ���������ɶ��̵߳ı�Ҫ�ԣ�һ�ǣ������ٶȽϿ죬
	 * *���ǣ��������������������������
	 */
	private String sharemetadata(String filename) throws ClassNotFoundException, IOException, InterruptedException{
		//����һ���ļ���Ԫ��Ϣ��DHT�����С�
		//System.out.println(filename);
		FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
		futureDHT.awaitUninterruptibly();
		//��DHT�в鵽�˸��ļ���Ԫ��Ϣ��
		if(futureDHT.isSuccess()){
			System.out.println("���������и��ļ���ԭʼ��Ϣ");
			FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//�������л�ȡ��Ԫ���ݡ�
			//Ԫ��Ϣ�����ˣ������ǲ���ȷ�ġ�
			if(meta.chunkNum()==-1){
				//���˻���ȷ��ɾ����
				FutureDHT re=peer.remove(Number160.createHash(filename)).start();
				re.await();
				if(re.isSuccess()){
					//���Ԫ��Ϣ
					sharemetadata(filename);
				}
				else{
					//�ܲ鵽��ɾ�����������ж����
					return "failed";
				}
			}
			else return "failed";
		}
		//DHT������û�и��ļ���Ԫ��Ϣ������ӡ�
		else{
			System.out.println("�����л�û������ļ���ԭʼ��Ϣ");
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
				System.out.println("�ļ�������");
				return "failed";
			}
		}
		return "ok";
	}
	public void  share(String filename) throws IOException, ClassNotFoundException, InterruptedException{
		//�����ļ�������Ϣ��DHT����.�Ȱ�Ԫ��Ϣ��������Ƭ��Ϣ��
		String result = sharemetadata(filename);
		System.out.println("share metadata "+result);
		if(result.equals("ok")){
			FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
			futureDHT.awaitUninterruptibly();
			if(futureDHT.isSuccess()){
				FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//�������л�ȡ��Ԫ���ݡ�
				InetAddress addr = InetAddress.getLocalHost();
				String ip=addr.getHostAddress().toString()+":"+port;//��ñ���IP:port,��Ϊ����Ψһ��ʾ��
				for(int i=0;i<meta.chunkNum();i++){
					peer.put(Number160.createHash(filename+"_"+i)).setData(Number160.createHash(ip), new Data(ip)).start().awaitUninterruptibly();
				}
			}
			else{
				System.out.println("��ȡ����Ԫ��Ϣ");
			}
		}
		else{
			System.out.println("Ԫ���ݷ���ʧ��");
		}
		//System.out.println("share chunk list ok");
	}
	

	
	/*
	 * �����������صķ�����һ���м�������һ��û��
	 */
	public List<Future<String>>BatchDownLoad(List<String> filenames){
		return BatchDownLoad(filenames,null);
	}
	public List<Future<String>>BatchDownLoad(List<String> filenames,List<DownLoadState> states ){
		//�������غö��ļ�
		if(filenames!=null && filenames.size()>0){
			List<Future<String>> futures = new ArrayList<Future<String>>();
			ExecutorService threadPool = Executors.newCachedThreadPool();
			/*
			 * forѭ���ڲ���Ҫ��Download�ڲ����Ĺ��̣����ж�������������͡�
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
					if(!indexfile.exists() && !datafile.exists()){//�µ���������ո�µġ�
						futures.add(threadPool.submit(new DownLoader(filenames.get(i),state)));
					}
					else if(indexfile.exists() && datafile.exists()){//������ڣ����������δ��ɵ�����
						futures.add(threadPool.submit(new DownloaderContinue(filenames.get(i),state)));
					}
					else{//����������������⣺����
						futures.add(threadPool.submit(new DownloaderCrash(state)));
					}
				}
			}
			threadPool.shutdown();//�ȴ����е����е��߳���ɺ󣬹ر��̳߳�
			return futures;
		}
		else
			return null;
	}
	/*
	 * ������������ĳ���ļ��ķ�����һ���м�������һ��û��.
	 */
	public Future<String> DownLoad(String filename,DownLoadState state){
		/*��Ҫ������ط����ж�һ�����ص����͡�
		 *����������������Downloader�ദ��
		 *���Ǽ������֮ǰδ��ɵ����� DownloaderContinue�ദ��
		 *����Ҫ����DownLoadError�ദ��
		 */
		if(filename!=null){
			File indexfile=new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			File datafile =new File(currentpath+File.separator+filename+Suffix.datafilesuffix);
			if(!indexfile.exists() && !datafile.exists() ){
				//����������ļ������ڣ�����Ϊ����һ��ո�µ�������Ϊ
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownLoader(filename,state));
				threadPool.shutdown();//�ȴ����������̶߳���ɺ󣬹ر��̳߳ء�
				return future;
			}
			else if(indexfile.exists() && datafile.exists()){
				//������ڣ�����Ϊ�Ǽ���������ǰû����ɵ�����
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownloaderContinue(filename,state));
				threadPool.shutdown();//�ȴ����������̶߳���ɺ󣬹ر��̳߳ء�
				return future;
			}
			else{
				ExecutorService threadPool=Executors.newSingleThreadExecutor();
				Future<String> future = threadPool.submit(new DownloaderCrash(state));
				threadPool.shutdown();//�ȴ����������̶߳���ɺ󣬹ر��̳߳ء�
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
		 * �����������������ࣺ
		 * ���������������ͣ�
		 * a:Դ�ļ����ڣ���Ҫ������
		 * b:���ݺ�index�ļ��е�һ��������ɾ�ˡ�
		 * ��֮���ݺ�index��һ����
		 * ��ʾ��Դ�ļ����ڻ���ʱ�ļ����ƻ���--��crash
		 */
		DownloaderCrash(DownLoadState state){
			super(null, state);
			
		}
		@Override
		public String call() throws Exception {
			// TODO Auto-generated method stub
			if(state!=null){
				state.setIsdone(true);//�����
				state.setIsok(false);//���ǽ���Ǵ��
				state.setPercent(0.0);//�����
			}
			return "crash";
		}
		
	}
	
	private class DownLoader implements Callable<String>{
		/*
		 * Ĭ�ϵ�ʵ����Ϊ�����������ʵģ����¿�����һ����������
		 * �ڲ��࣬��������һ���ļ�,��������һ���µ��ļ���
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
			//�����ܸ��ݲ�ͬ�ĵ������ѡ��ͬ�Ĳ��ԡ�
		/*	File indexfile=new File(currentpath+File.separator+filename+"_index");
			if(indexfile.exists()){//filename_index�ļ����ڣ���Ҫ�Ӵ��̻�ȡ��������.
				
			}
		*/
			return download(filename);
		}
		 /* ��������ص��ĸ������������getFileMetaDataFromDHT��getchunkslistFromDHT��downloadFresh
		 * �Ǽ�������ʱո�µġ�
		 * getFileMetaDataFromDHT:��DHT�����еõ��ļ���Ԫ��Ϣ�����õ��ļ���С���ļ�chunk��С��chunk������
		 * getchunkslist:��DHT�����еõ�ÿ��chunk��ӵ�����б�
		 * downloadchunks:�������е�chunk���ֳ�20���̣߳�ÿ���̸߳�����һ���֡�
		 * download�����غ����������ļ�filename��
		 * --------
		 * *��������⼸�������ŵ�һ���ڲ����У�����ڲ���ʵ��Callable�ӿڣ������Ļ���
		 * *һ����������Ͷ�Ӧһ���̣߳����Զ��߳����ء�
		 * --------
		 * *���ڲ���������ǣ�����ʱ��Ҫpeer��currentpath����Щ�������뵱������������ȥ��
		 * *���ڲ�����Է����ⲿ��ĳ�Ա�����Ծ���ô�ɰ�
		 */
		private FileMetadata getFileMetaData(String filename ) throws ClassNotFoundException, IOException{
			//��DHT�����еõ�ĳ���ļ���meta��Ϣ
			FutureDHT futureDHT =peer.get(Number160.createHash(filename)).start();
			futureDHT.awaitUninterruptibly();
			if(futureDHT.isSuccess()){
				FileMetadata meta=(FileMetadata)futureDHT.getData().getObject();//�������л�ȡ��Ԫ���ݡ�
				return meta;
			}
			else
				return null;
		}
		private List<ChunkHolderList> getchunkslist(String filename) throws ClassNotFoundException, IOException{
			List<ChunkHolderList> lists = new ArrayList<ChunkHolderList>();
			//���Ի�ȡԪ��Ϣ��
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
				return lists;//����ò���Ԫ��Ϣ�����ؿ��б�
			}
			//���Ի�ȡchunk�б���Ϣ��
			long chunknum = meta.chunkNum();
			FutureDHT chunkDHT=null;
					
			//��i��chunk��ip:port��ʽ����Ϣ
			System.out.println("get chunk list ...");
			
			for(int i=0;i<chunknum;i++){
				chunkDHT=peer.get(Number160.createHash(filename+"_"+i)).setAll().start();
				chunkDHT.awaitUninterruptibly();
				//chunkDHT.getData().getObject():�����õ�����ָ���һ��������ĵ�����,
						
				/*//ֻ�����һ��ֵ
				if(chunkDHT.isSuccess()){
					System.out.println(chunkDHT.getData().getObject().toString());
				}
				*/
						
				//����������е�ֵ,��������chunk�� ChunkHolderList��
						
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
					System.out.println("��chunk��ʱ����ȡ");
				}
			}
			//System.out.println("get chunk list done");
			return lists;
		}
		protected int downloadchunks(List<ChunkHolderList>lists,int allchunknum,String filename) throws InterruptedException, ExecutionException{
			/*���������£�
			 * ����ͬʱ����20���̲߳������أ�ÿ���̸߳������ؾ����һ��chunk����һ�����ؽ����󣬿�����һ����
			 * Ҳ��������������Ч�ʻ��Щ������20���̣߳�ÿ���̸߳�����chunk��1/20,ÿ���̰߳��������������
			 * ����chunk����20���̻߳᷵��ִ�н�������ɹ����ص�chunks��Ŀ����20���̻߳�ͬ����дfilename_data
			 * ��filename_index�ļ���
			 * 
			 * ��allchunknum==lists.size,����ÿ���̶߳��ɹ������������������chunk����Ҫ�ϲ�filename_data�ļ�����ʱ
			 * ��Ҫ filename_index�İ������ϲ���ɺ�ɾ��filename_data��file_index�ļ�������߳��첽���������ݣ�����
			 * д�뵽filename_data��filename_index�ļ���ʱ����Ҫͬ������Ҫ������Ҫ�����ļ�ͬ��д�롣�ڶ��㣬�õ����ṩ���ط����ͬʱ�����ṩ����
			 * �������ط�����Ҫ��ȡfilename_data��filename_index���ݣ���ֻ���ģ�����Ҫ������
			 * 
			 */
			Object lock = new Object();//����������ͬ��filename_data��filename_index��д�롣
			int chunknum=lists.size();
			int chunksperthread = chunknum/downloadthreads+1;//ÿ���̸߳����chunk����Ŀ��
			//�ַ����񣬷���20���̡߳�
			ExecutorService service = Executors.newFixedThreadPool(20);
			List<Future<Integer>> result = new ArrayList<Future<Integer>>();//��¼���ؽ��
			for(int i=0;i<chunknum;i=i+chunksperthread){
				DownloadThread thread=null;
				if(i+chunksperthread < chunknum )
					thread =new DownloadThread(lists.subList(i, i+chunksperthread),peer,port,filename,currentpath,lock,state);
				else
					thread =new DownloadThread(lists.subList(i, chunknum),peer,port,filename,currentpath,lock,state);
				Future<Integer> future=service.submit(thread);
				result.add(future);
			}
			service.shutdown();//�ȴ�20���̶߳�������
			
			int num=0;
			for(Future<Integer> f:result)
				num+=f.get();
			return num; //���سɹ����ص�chunk��Ŀ��
		}
		private String download(String filename) throws ClassNotFoundException, IOException, InterruptedException, ExecutionException{
			System.out.println("download...");
			FileMetadata meta = getFileMetaData(filename);
			
			if(meta==null){ //�������Ҳ�������ļ��������Ϣ,ֱ�ӷ���ʧ����Ϣ
				if(state!=null){
					state.setIsdone(true);//�Ѿ���ɡ�
					state.setIsok(false);//���ǽ���Ǵ��
					state.setPercent(0.0);//��������ʡ�
					state.setChunknum(0);
				}
				return "download is failed";
			}
			if(state!=null){
				state.setIsdone(false);
				state.setIsok(false);
				state.setFilename(filename);//�����ļ���
				state.setPercent(0.0);//�¿�ʼ������
				state.setChunknum(meta.chunkNum());//��Ҫ���ص�chunk��Ŀ
			}
			
			/*
			 * ������Ҫ���ϴ���data��index�ļ���Ȼ���meta����index�Ĵ��롣
			 * ����DownThread�Ͳ��ô�����ļ����߼��ˣ�ֻҪд�Ϳ����ˡ�
			 */
			File datafile = new File(currentpath+File.separator+filename+Suffix.datafilesuffix);
			datafile.createNewFile();
			
			File indexfile = new File(currentpath+File.separator+filename+Suffix.indexfilesuffix);
			indexfile.createNewFile();
			RandomAccessFile ifile= new RandomAccessFile(indexfile,"rw");
			ifile.writeLong(meta.fileLength());//д���ļ���С
			ifile.seek(8);
			ifile.writeLong(meta.chunkSize());//д��chunk��С 
			ifile.seek(16);
			ifile.writeLong(meta.lastChunkSize());//д�����һ����Ĵ�С
			ifile.seek(24);
			ifile.writeLong(meta.chunkNum());//�õ�chunk��Ŀ.
			ifile.close();
			
			
			int allchunknum =(int) meta.chunkNum(); //�ܹ��������chunk
			
			List<ChunkHolderList>lists=getchunkslist(filename);
			int availablechunknum = lists.size(); //��������chunk����Ϣ�ǿ�ȡ�ġ�
			
			int downloaded=downloadchunks(lists,allchunknum,filename); //�ɹ���������ô���chunk��
			System.out.println("�ܹ� ��"+allchunknum);
			System.out.println("�� :"+availablechunknum+"��ȡ");
			System.out.println("�ɹ�����: "+downloaded);
			if(allchunknum==downloaded){//�ɹ����������еķ�Ƭ���ɹ��ˡ�
				//ɾ��filename_index�ļ�
				//File indexfile=new File(currentpath+File.separator+filename+"_index");
				if(indexfile.isFile() && indexfile.exists()){
					boolean success=indexfile.delete();
					if(success){
		//				System.out.println("delete index file success");
					}
		//			System.out.println("all chunks are downloaded successfully");
				}
				if(state!=null){
					state.setIsdone(true); //�����
					state.setIsok(true);   //��ȷ�������
					state.setPercent(100.0); //���������
				}
				return "download is ok ";
			}
			else{
				if(state!=null){
					state.setIsdone(true);//�����
					state.setIsok(false); //���ǽ���Ǵ���ģ������س�������
				}
				return "download is failed";
			}
			
		}

	}
	
	private class DownloaderContinue extends DownLoader{
		/*
		 * ����ฺ��ʵ�����ء������ӹ��̡�.
		 * ��Ҫ�ӱ��صĻ����ļ���ȡ�����Ϣ��
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
			//������Ҫ���һ��ChunkHolderlist���б�ip_pors���������Ȳ����
		/*	private List<String> ip_pors;  //ӵ�и�chunk�Ļ����б�
			private String filename;       //�ļ�����
			private long chunk_num;        //���ļ���chunk��Ŀ
			private long chunk_id;         //���chunk�ı��
			private long normalchunk_size; //����chunk�Ĵ�С��
			private long thischunk_size;   //���chunk�Ĵ�
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
			ifile.close();//�����ļ�Ҫ�ǵùر�
			all.removeAll(downloaded);
			/*��ʱ��all����ŵľ���������Ҫ���ص�chunk��chunkid
			 *���ChunkHolderList������lists����Ϳ����ˡ�
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
					state.setIsdone(true);//�Ѿ���ɡ�
					state.setIsok(false);//���ǽ���Ǵ��
					state.setPercent(0.0);//��������ʡ�
					state.setChunknum(0);
				}
				return "download is failed";
			}
			
			if(state!=null){
				state.setIsdone(false);
				state.setIsok(false);
				state.setFilename(filename);//�����ļ���
				state.setPercent(0.0);//�¿�ʼ������
				state.setChunknum(meta.chunkNum());//��Ҫ���ص�chunk��Ŀ
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
			System.out.println("�ܹ�: "+allchunknum);
			System.out.println("�ɹ����أ�"+downloaded);
			if(allchunknum==downloaded){
				/*
				 * �ɹ����������еķ�Ƭ����Ҫɾ��index�ļ�
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
