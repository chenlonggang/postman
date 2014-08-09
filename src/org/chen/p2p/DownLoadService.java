package org.chen.p2p;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.zip.CRC32;

public class DownLoadService implements Runnable {
	static int port=0;
	static String path=null;
	public DownLoadService(int port,String path){
		this.port=port;
		this.path=path;
		
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		ServerSocket server=null;
		try{
			//请求队列长度为50，当队列满时，后续的请求会被丢弃。
			server = new ServerSocket(port,50);
			while(true){
				Socket socket =server.accept();
				//invoke1(socket);
				invoke2(socket);
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	//这个处理过程放弃了，主要是因为filename_data和filename_index的存储结构变了。
	private static void invoke2(final Socket client) throws IOException{
		new Thread(new Runnable(){
			public void run(){
				ObjectInputStream in=null;
				//out,负责输出数据，从文件中读取数据，输出给客户端。
				OutputStream netOut = null;
				OutputStream out=null;
				try{
					in = new ObjectInputStream(client.getInputStream());
					GetChunk get = (GetChunk)in.readObject();
					//准备好out，准备数据输出 
					netOut= client.getOutputStream();
					out = new DataOutputStream(new BufferedOutputStream(netOut));
					
			//		System.out.println("收到的下载请求如下：");
			//		System.out.println("文件名: "+get.getFilename());
			//		System.out.println("chunkid: "+get.getChunkid());
			//		System.out.println("Normalchunksize:"+get.getNormalChunksize());
			//		System.out.println("ThisChunkSize: "+get.getThisChunkSize());
					
					String filename = path+File.separator+get.getFilename();//从该文件读取数据。
					long normalchunksize=get.getNormalChunksize();//得到正常的chunk的大小。		
					long thischunksize  =get.getThisChunkSize();//得到该chunk的大小，纪要传送的B数目。
					long chunkid = get.getChunkid();//得到该chunk的id
					File f = new File(filename);
					if(f.isFile() && f.exists()){ //数据文件是存在的，直接定位到那个位置，读取数据就ok了
						RandomAccessFile file = new RandomAccessFile(f,"r");
						out.write(0);//预示着数据找着了。
						out.flush();
						byte[] buf = new byte[1024*8];
						long offset = chunkid*normalchunksize;//得到文件的偏移量。
						long bytes = thischunksize;//本次需要读取的bytes数。
						file.seek(offset);//文件读指针移动到指定位置。
						int num  = file.read(buf);
						CRC32 crc32 = new CRC32();
						while(num!=(-1)){//接着获取数据，发送数据。
							//System.out.println(num);
							if(bytes-num>=0){//本次读取的num个数据都是要发送的
								out.write(buf,0,num);
								out.flush();
								crc32.update(buf,0,num);
								bytes = bytes -num;
								num=file.read(buf);//接着读数据；
							}
							else{//现在只发送buf里面的前bytes个byte的数据。
								out.write(buf,0,(int)bytes);
								out.flush();
								crc32.update(buf,0,(int)bytes);
								break;
							}
						}
						//System.out.println(get.getChunkid()+" "+Long.toHexString(crc32.getValue()).toUpperCase());
						/*
						 * 这里发送CRC32校验码
						 */
						long crc = crc32.getValue();
						byte [] d =CRC32ToBytes(crc);
						out.write(d, 0, 4);
						out.flush();
						file.close();//源数据文件关闭
					}
					else{
						out.write(1);
						out.flush();
					}
				}catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e){
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally{
					try {
						out.close();
						in.close();
						out=null;
						in=null;
						client.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}

			private byte[] CRC32ToBytes(long crc) {
				// TODO Auto-generated method stub
				//return null;
				byte [] data = new byte[4];
				data[0]=(byte) ((crc>>>24)&0xff);
				data[1]=(byte) ((crc>>>16)&0xff);
				data[2]=(byte) ((crc>>>8)&0xff);
				data[3]=(byte) (crc&0xff);
				return data;
			}
		}).start();
	}
	private static void invoke1(final Socket client) throws IOException {
		new Thread(new Runnable() {
				public void run() {
					ObjectInputStream in = null;
					//这里需要一个out，负责输出数据.数据输出格式为：int+byte数组。int为0表示数据找到了，为1表示找不到，此时后面更一个buebye。
					OutputStream netOut =null; 
					OutputStream  out=null;
					try {
						in = new ObjectInputStream(client.getInputStream());
						GetChunk get=(GetChunk)in.readObject();
						//准备好out，准备数据输出
						netOut=client.getOutputStream();
						out  =  new  DataOutputStream(new  BufferedOutputStream(netOut)); 
						
						System.out.println("收到的下载请求如下：");
						System.out.println("文件名: "+get.getFilename());
						System.out.println("chunkid: "+get.getChunkid());
						System.out.println("Normalchunksize:"+get.getNormalChunksize());
						System.out.println("ThisChunkSize: "+get.getThisChunkSize());
						
						String filename =path+File.separator+ get.getFilename();//从该文件读取数据。
						long normalchunksize=get.getNormalChunksize();//得到正常的chunk的大小。
						long thischunksize  =get.getThisChunkSize();//得到该chunk的大小，纪要传送的B数目。
						long chunkid = get.getChunkid();//得到该chunk的id
						File f = new File(filename);
						if(f.exists() && f.isFile()){//源文件存在，即文件是完整的，则用randomaccessfile读取一个片段。
							// 创建文件流用来读取文件中的数据 
							System.out.println("源文件存在");
							RandomAccessFile file = new RandomAccessFile(f,"r");
							out.write(0);//预示着数据找着了。
							out.flush();
							byte[] buf = new byte[2048];
							long offset = chunkid*normalchunksize;//得到文件的偏移量。
							long bytes = thischunksize;//本次需要读取的bytes数。
							file.seek(offset);//文件读指针移动到指定位置。
							int num  = file.read(buf);
							while(num!=(-1)){//接着获取数据，发送数据。
								//System.out.println(num);
								if(bytes-num>=0){//本次读取的num个数据都是要发送的
									out.write(buf,0,num);
									out.flush();
									num=file.read(buf);//接着读数据；
									bytes = bytes -num;
								}
								else{//现在只发送buf里面的前bytes个byte的数据。
									out.write(buf,0,(int)bytes);
									out.flush();
									break;
								}
							}
							file.close();//源数据文件关闭
							
						}
						else{//源文件不存在，尝试，filename_data文件和filename_index是否都从在
							//String data=filename+"_"+"data";
							//String index = filename+"_"+"index";
							String data=path+File.separator+filename+Suffix.datafilesuffix;
							String index=path+File.separator+filename+Suffix.indexfilesuffix;
							File dfile = new File(data);
							File ifile = new File(index);
							if(dfile.exists()&&dfile.isFile()&&ifile.exists()&&ifile.isFile()){//都在，从这里读取要发送的数据。
								//准备从这里读数据。从filename_data和filename_index得到数据
								RandomAccessFile datafile = new RandomAccessFile(dfile,"r");
								RandomAccessFile indexfile= new RandomAccessFile(ifile,"r");
								out.write(0);
								out.flush();
							}
							else{//无法找到合适的数据源，发送1，表失败。
							out.write(1);
							out.flush();
							}
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}finally{
						//处理完毕，关闭流、socket
						try {
							if(out!=null)out.close();
							if(in!=null)in.close();
							out=null;
							in=null;
							if(client!=null)client.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}	
					}
				}
		}).start(); 
	}
}
