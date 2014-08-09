
/*
 * �ļ��ṹ���£�
 * filename_data�����ݣ������ع����У�������ݿ����ǡ��ӿ����ݡ���
 * filename_index�Ǽ�¼��Щchunk�ѳɹ����ص��ļ�����һ�����¼chunk����
 * �����¼ÿ�γɹ����ص�chunk��id��
 */
/*
 * chunk�����ʽ���£�statues+data+CRC32
 * statues����ʾ�������û���ҵ����ݣ���statues���滹��û�����ݡ�
 *          ���statuesΪ0�����ʾ�����滹�����ݡ�
 *          ���statuesΪ1�����ʾ������û�������ˡ�
 *          һ��byte
 * data���ļ�����,
 * CRC32��data���CRC32У��ֵ��
 *        32λ16���Ƶ��ַ�����ʽ��8bytes��
 *        
 *  ��Ҫ�Ķ����ࣺDownloadThread��DownLoadService�еķ�����
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
	private int port;//peer������˿��Ϲ���
	private String filename; //�ļ���
	private String path;//����·��
	private Object lock;//������
	private DownLoadState state;//����״̬���� 
	
	
	/*���ظ�chunk����socket��������˭��chunk�����У�ע��portҪ��1��
	chunk�����port��DHTPeer֮�佻����poer
	�ṩ���ط����port��Ĭ�������port�ļ�1
	����������״̬�����ˣ�ʧ���ˡ�ok��failed��
	�������أ���socket���趨�������ʱ��
	������سɹ���д��filename_data��filename_index�ļ���braek
	*/

	private void getAbdSaveData1(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
	
		byte[] buf = new byte[2048];
		int num = in.read(buf);
		//System.out.println(num);
		long offset = datafile.length();
		datafile.seek(offset);//�ļ�ָ���Ƶ��ļ�ĩβ
		while (num != (-1)) {
			//System.out.println(num);
			datafile.write(buf, 0, num);
			offset=offset+num;
			datafile.seek(offset);
			num=in.read(buf);
		}
		//data����д�����
		//��ʼд��indexfile
		indexfile.writeLong(chunk.getChunk_id());
		indexfile.writeLong(offset);
		indexfile.writeLong(chunk.getThisChunkSize());
		datafile.close();
		indexfile.close();
	}
	/*
	 * д���߼�Ҫ�ģ��ɵķ����У����յ������ļ�filename_data����Ҫ���filename_index�ļ����ϲ��ġ�
	 * �����Ļ����ṩ���ط���ʱ����ȡ���ݸ�����ʱ���������߼���һ�����������������������У�һ������������filename_data
	 * �л�ȡ���ݵ������
	 * ���������ɣ���RandomAccessFile��seekֱ��������Ӧ��λ�ã�ֱ��д����ȷ��λ�þͿ�����
	 * ������֮���Ч�����ṩ���ط���ʱ����ȡ���ݵ��߼�ֻ��һ��������seek����Ӧλ�ã�Ȼ������ݣ����˺ϲ�����Ҳ�����ṩ���ݵ��߼�
	 * �Դ�ϲ���Ǻ�
	 * 
	 * �����������ǣ��ɷ����У�_index��_inddex�ļ�д������ʱ��һֱ��ĩβ׷�ӵķ�ʽ����ͷ�ƶ��Ĵ��۽�С
	 * �����µ������ǣ���RandomAccessFile������д��Ĺ����д�ͷ���ܻ�ǰǰ�����ƶ������յ�д�����ܿ϶�û��
	 * ĩβ׷�ӵķ�ʽ�ã��������װɡ�
	 */
	private void getAndSaveData2(InputStream in,RandomAccessFile datafile,RandomAccessFile indexfile,ChunkHolderList chunk) throws IOException{
		//һ������£�normalchunksize==thischunksize,���һ�����ʱ��һ����
		long normalchunksize= chunk.getNormalChunksize();//�õ����С��
		long thischunksize  = chunk.getThisChunkSize();//�õ������Ĵ�С��
		long id=chunk.getChunk_id();//�õ����chunk��id.
		byte[] buf = new byte[2048];
		int num= in.read(buf);
		long offset = normalchunksize*id;//�õ�������ƫ������
		datafile.seek(offset);
		while(num!=(-1)){
			datafile.write(buf,0,num);
			offset=offset+num;
			datafile.seek(offset);
			num=in.read(buf);
		}
		//data�ļ�д�����
		//��ʼд��indexfile���ݣ�д��chunkid,�ǵý�indexfile��дָ���λ�ú��Ƶ��ļ�ĩβ��
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
				  * ���uodateһ��Ҫ�ŵ�bytes=size��ǰ�棬��Ȼ����ѽ
				  * �ӵ�ѽ������4��Сʱ.
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
			//data�ļ�д����ɣ���ʼд��index�ļ���
			writeindex(indexfile,id);
		}
		else{
			/*
			 * ���CRC32У��ʧ�ܣ������ʾ���׳��쳣��downloadchunk�Ჶ�񲢴���.
			 */
			System.out.println(chunk.getChunk_id()+" "+Long.toHexString(crcnow).toUpperCase()+" "+Long.toHexString(crc32.getValue()).toUpperCase());
			throw new IOException("socket error: crc32 change!");
		}
		try{
			//д��֮�󣬹ر��ļ����
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
		//data�ļ�д����ɣ���ʼд��index�ļ���
		writeindex(indexfile,id);
		//д��֮�󣬹ر��ļ����
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
	/*index��ʾҪ��address�����ȡ��chunk�����ݣ�peer��Ĭ��DownService�˿�Ϊpeer�����˿�+1.
	 * ������������߼������⣬��Ӧ���Ƚ���socket���ӣ����ж�Ҫ��Ҫ���ء�����ж��ǲ������صģ������socket�����������˽��գ�
	 * ���쳣��.
	 * ���߼������ж�Ҫ��Ҫ�������أ��ٷ�socket��
	 * �ô��ǣ��Ǹ��쳣����û�ˣ����Ҿ������ڿͻ����ж�Ҫ��Ҫ����socket���ӣ������Ϳ��Լ���һЩ����Ҫ�����ӣ�
	 * socket���ӵĴ���Ҳ��С����ʡ��ʡ
	 */
	/*
	 * 2014.8.5:
	 * ������ж��߼���߶�DHTPeer����ȥ���������ֻ��Ҫת�͸������ء�д���ݾͿ����ˡ�
	 * д�����õ��ļ�Ҳ�Ѿ���DHTPeer��DownLoader���download�����������ˡ�
	 * ִ�е������ʱ��һ��׼���������Ѿ�ʵ��׼���׵���ֱ��д���ݾͿ����ˡ�
	 */
	public String DownloadChunk2(ChunkHolderList chunk,String address){
		String [] parts= address.split(":");
		String ip=parts[0];
		int port = Integer.parseInt(parts[1])+1;
		
		GetChunk command = new GetChunk(chunk.getFilename(),chunk.getChunk_num(),chunk.getChunk_id(),chunk.getNormalChunksize(),chunk.getThisChunkSize());
		//�ͷ���������socket���ӡ�
		Socket socket=null;
		ObjectOutputStream out=null;
		InputStream netIn =null; 
		InputStream in=null;
		String result="failed";
		/* 
		 *���ķ�Χ̫���ˣ�����ʵ������ʵ���ǵ��̵߳� 
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
				//���ڽ���socket���ӣ���������
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
				
			}catch(Exception e){//�κε��쳣���������¸ô����س���ʧ�ܡ�
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
	 * ��ͼ��С���ķ�Χ���ò�����Ĳ���
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
			//����socket���ӣ���������
	//		System.out.println(ip+": "+port);
			socket = new Socket(ip,port);
			socket.setSoTimeout(50000);
			/*
			 * ���������ȴ�����,������������ʱ�䣬��Ȼû�����ݣ�read���׳�һ��SocketTiemoutException,
			 * ������Ҫ��������쳣�����ǵ�ǰ��socket������Ȼ����Ч�ġ�����Է����̱�����崻���������Ͽ���
			 * ���ص�read��һֱ������ȥ����ʱ���ó�ʱʱ��ͷǳ���Ҫ������read���̻߳�һֱ����
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
	 * ����chunk��Ƭ����ַ��address��
	 */
	public String DownloadChunk(ChunkHolderList chunk,String address){
		String [] parts= address.split(":");
		String ip=parts[0];
		int port = Integer.parseInt(parts[1])+1;
		//System.out.println("ip��"+parts[0]+" port: "+port);
		//��װ��get���
		GetChunk command = new GetChunk(chunk.getFilename(),chunk.getChunk_num(),chunk.getChunk_id(),chunk.getNormalChunksize(),chunk.getThisChunkSize());
		//�ͷ���������socket���ӡ�
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
				//������Ҫд�ļ��ˣ���ʱ��Ҫ������
				synchronized(lock){
					//��һ�����ж�filename_data��filename_index�ļ��Ƿ���ڣ���������ڣ�������
					//�ڶ�����д�����ݡ�
					//String data=path+File.separator+filename+"_data";
					//String index=path+File.separator+filename+"_index";
					String data=path+File.separator+filename+Suffix.datafilesuffix;
					String index=path+File.separator+filename+Suffix.indexfilesuffix;
					File dfile = new File(data);
					File ifile = new File(index);
		
					//ֻ���������ļ������ڣ�����Ϊ�����ڡ�,���ֻ��һ����û�У����Ǵ��
					if(dfile.exists() && ifile.exists()){//���ڡ�ֱ�Ӵ�
						
						RandomAccessFile datafile = new RandomAccessFile(dfile,"rw");
						RandomAccessFile indexfile= new RandomAccessFile(ifile,"rw");
						getAndSaveData2( in, datafile, indexfile, chunk);
						result ="ok";
					}
					else if(!dfile.exists() && !ifile.exists()){//�����ڣ��Ƚ����ļ����ٴ�
						ifile.createNewFile();//����һ���������������ļ�
						dfile.createNewFile();//����һ��������ݵ��ļ�
						RandomAccessFile datafile = new RandomAccessFile(dfile,"rw");
						RandomAccessFile indexfile= new RandomAccessFile(ifile,"rw");
						//indexfile�ĵ�һ��ֵ��ʾ�м������ļ��м���chunk������ǳɹ����ص�chunkid��
						
						//�½�ʱ����д��chunknum������дָ��ƫ������
						indexfile.writeLong(chunk.getChunk_num()); 
						indexfile.seek(8);
						getAndSaveData2( in, datafile, indexfile, chunk);
						result ="ok";
					}
					else{//ֻ��һ���ڣ�fuckaway
						System.out.println("[*_index��*_data����Գ��ֵģ�ɾ���������ֵ�*_index��*_data�ļ������¿�ʼ]");
						result="failed";
					}
				}
				
			}
			else{//û���ҵ����ݡ�
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
		//˳�����ظ��̸߳�������з�Ƭ�����سɹ����أ�flush��chunk��Ŀ��
		for(ChunkHolderList chunk:lists){//����chunk��Ƭ
			//List<String> oweners = chunk.getIp_pors();//�õ�ӵ��chunk��Ƭ�Ļ����б�
			List<String> oweners=chunk.RefreshIp_Ports(this.peer); //ʵʱģʽ�����Խ����ȵ㡣
			String result="failed";
			
			if(oweners!=null && oweners.size()>0){ //���õ���oweners��Ϊnull�����������ж���ʱ����ʼ���أ����򣬲����ء�
				int times=oweners.size()>3?3:oweners.size();//ÿ��chunk��ೢ��3�Σ������ʧ�ܣ������
				
				Random rdm = new Random(System.currentTimeMillis()); //�Ե�ǰʱ��Ϊ���ӣ����������
			
				while(times > 0){
					//������ɹ���������һ��
					int index = Math.abs(rdm.nextInt())%oweners.size();//���ɵ�index�ķ�ΧΪ[0...oweners-1]��
					result = DownloadChunk3(chunk,oweners.get(index));//ip:port��ʽ
					if(result=="ok"){
						//System.out.println(chunk.getChunk_id()+" is download ok");
						break;
					}
					else
						times--;
				}
			}
			//������3�����ϻ��ߣ�oweners����û�ж���ʱ��result=failed��ʧ���ˡ�
			//�������سɹ����ɹ�����1,�����Լ���ӵ�DHT������,
			if(result=="ok"){
				successed=successed+1;
				if(state!=null){
					synchronized(lock){
						state.IncreasePercent(1.0/state.getChunknum());
					}
				}
			
				try{
					
					InetAddress addr = InetAddress.getLocalHost();
					String ip=addr.getHostAddress().toString()+":"+port;//��ñ���IP:port,��Ϊ����Ψһ��ʾ��
					peer.put(Number160.createHash(filename+"_"+chunk.getChunk_id())).setData(Number160.createHash(ip), new Data(ip)).start().awaitUninterruptibly();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		return successed;
	}
}
