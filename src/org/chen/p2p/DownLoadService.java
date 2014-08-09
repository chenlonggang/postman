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
			//������г���Ϊ50����������ʱ������������ᱻ������
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
	
	//���������̷����ˣ���Ҫ����Ϊfilename_data��filename_index�Ĵ洢�ṹ���ˡ�
	private static void invoke2(final Socket client) throws IOException{
		new Thread(new Runnable(){
			public void run(){
				ObjectInputStream in=null;
				//out,����������ݣ����ļ��ж�ȡ���ݣ�������ͻ��ˡ�
				OutputStream netOut = null;
				OutputStream out=null;
				try{
					in = new ObjectInputStream(client.getInputStream());
					GetChunk get = (GetChunk)in.readObject();
					//׼����out��׼��������� 
					netOut= client.getOutputStream();
					out = new DataOutputStream(new BufferedOutputStream(netOut));
					
			//		System.out.println("�յ��������������£�");
			//		System.out.println("�ļ���: "+get.getFilename());
			//		System.out.println("chunkid: "+get.getChunkid());
			//		System.out.println("Normalchunksize:"+get.getNormalChunksize());
			//		System.out.println("ThisChunkSize: "+get.getThisChunkSize());
					
					String filename = path+File.separator+get.getFilename();//�Ӹ��ļ���ȡ���ݡ�
					long normalchunksize=get.getNormalChunksize();//�õ�������chunk�Ĵ�С��		
					long thischunksize  =get.getThisChunkSize();//�õ���chunk�Ĵ�С����Ҫ���͵�B��Ŀ��
					long chunkid = get.getChunkid();//�õ���chunk��id
					File f = new File(filename);
					if(f.isFile() && f.exists()){ //�����ļ��Ǵ��ڵģ�ֱ�Ӷ�λ���Ǹ�λ�ã���ȡ���ݾ�ok��
						RandomAccessFile file = new RandomAccessFile(f,"r");
						out.write(0);//Ԥʾ�����������ˡ�
						out.flush();
						byte[] buf = new byte[1024*8];
						long offset = chunkid*normalchunksize;//�õ��ļ���ƫ������
						long bytes = thischunksize;//������Ҫ��ȡ��bytes����
						file.seek(offset);//�ļ���ָ���ƶ���ָ��λ�á�
						int num  = file.read(buf);
						CRC32 crc32 = new CRC32();
						while(num!=(-1)){//���Ż�ȡ���ݣ��������ݡ�
							//System.out.println(num);
							if(bytes-num>=0){//���ζ�ȡ��num�����ݶ���Ҫ���͵�
								out.write(buf,0,num);
								out.flush();
								crc32.update(buf,0,num);
								bytes = bytes -num;
								num=file.read(buf);//���Ŷ����ݣ�
							}
							else{//����ֻ����buf�����ǰbytes��byte�����ݡ�
								out.write(buf,0,(int)bytes);
								out.flush();
								crc32.update(buf,0,(int)bytes);
								break;
							}
						}
						//System.out.println(get.getChunkid()+" "+Long.toHexString(crc32.getValue()).toUpperCase());
						/*
						 * ���﷢��CRC32У����
						 */
						long crc = crc32.getValue();
						byte [] d =CRC32ToBytes(crc);
						out.write(d, 0, 4);
						out.flush();
						file.close();//Դ�����ļ��ر�
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
					//������Ҫһ��out�������������.���������ʽΪ��int+byte���顣intΪ0��ʾ�����ҵ��ˣ�Ϊ1��ʾ�Ҳ�������ʱ�����һ��buebye��
					OutputStream netOut =null; 
					OutputStream  out=null;
					try {
						in = new ObjectInputStream(client.getInputStream());
						GetChunk get=(GetChunk)in.readObject();
						//׼����out��׼���������
						netOut=client.getOutputStream();
						out  =  new  DataOutputStream(new  BufferedOutputStream(netOut)); 
						
						System.out.println("�յ��������������£�");
						System.out.println("�ļ���: "+get.getFilename());
						System.out.println("chunkid: "+get.getChunkid());
						System.out.println("Normalchunksize:"+get.getNormalChunksize());
						System.out.println("ThisChunkSize: "+get.getThisChunkSize());
						
						String filename =path+File.separator+ get.getFilename();//�Ӹ��ļ���ȡ���ݡ�
						long normalchunksize=get.getNormalChunksize();//�õ�������chunk�Ĵ�С��
						long thischunksize  =get.getThisChunkSize();//�õ���chunk�Ĵ�С����Ҫ���͵�B��Ŀ��
						long chunkid = get.getChunkid();//�õ���chunk��id
						File f = new File(filename);
						if(f.exists() && f.isFile()){//Դ�ļ����ڣ����ļ��������ģ�����randomaccessfile��ȡһ��Ƭ�Ρ�
							// �����ļ���������ȡ�ļ��е����� 
							System.out.println("Դ�ļ�����");
							RandomAccessFile file = new RandomAccessFile(f,"r");
							out.write(0);//Ԥʾ�����������ˡ�
							out.flush();
							byte[] buf = new byte[2048];
							long offset = chunkid*normalchunksize;//�õ��ļ���ƫ������
							long bytes = thischunksize;//������Ҫ��ȡ��bytes����
							file.seek(offset);//�ļ���ָ���ƶ���ָ��λ�á�
							int num  = file.read(buf);
							while(num!=(-1)){//���Ż�ȡ���ݣ��������ݡ�
								//System.out.println(num);
								if(bytes-num>=0){//���ζ�ȡ��num�����ݶ���Ҫ���͵�
									out.write(buf,0,num);
									out.flush();
									num=file.read(buf);//���Ŷ����ݣ�
									bytes = bytes -num;
								}
								else{//����ֻ����buf�����ǰbytes��byte�����ݡ�
									out.write(buf,0,(int)bytes);
									out.flush();
									break;
								}
							}
							file.close();//Դ�����ļ��ر�
							
						}
						else{//Դ�ļ������ڣ����ԣ�filename_data�ļ���filename_index�Ƿ񶼴���
							//String data=filename+"_"+"data";
							//String index = filename+"_"+"index";
							String data=path+File.separator+filename+Suffix.datafilesuffix;
							String index=path+File.separator+filename+Suffix.indexfilesuffix;
							File dfile = new File(data);
							File ifile = new File(index);
							if(dfile.exists()&&dfile.isFile()&&ifile.exists()&&ifile.isFile()){//���ڣ��������ȡҪ���͵����ݡ�
								//׼������������ݡ���filename_data��filename_index�õ�����
								RandomAccessFile datafile = new RandomAccessFile(dfile,"r");
								RandomAccessFile indexfile= new RandomAccessFile(ifile,"r");
								out.write(0);
								out.flush();
							}
							else{//�޷��ҵ����ʵ�����Դ������1����ʧ�ܡ�
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
						//������ϣ��ر�����socket
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
