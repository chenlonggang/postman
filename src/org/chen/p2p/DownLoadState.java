package org.chen.p2p;

import java.text.DecimalFormat;

/*
 * �������Ҫ����ʵ�ֶ�����״̬�ļ�¼
 * ��Ӧһ���Ծ����ļ�����������
 * 
 * �ڿͻ���ʹ�������ʱ����Ҫȥ��set**�ı���Щ���Ե�ȡֵ��
 * ��Щֵ�������������ͣ���չ������Զ����á����
 */
public class DownLoadState {

	private String filename=null;//���������Ǹ��ļ�����������
	private boolean isdone=false;//�������
	private boolean isok=false;//��ȷ�������
	private double percent=0.0;//��ɵİٷֱȡ�
	private long chunknum=0;
	public long getChunknum(){
		return chunknum;
	}
	public void setChunknum(long num){
		this.chunknum=num;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public boolean isIsdone() {
		return isdone;
	}
	public void setIsdone(boolean isdone) {
		this.isdone = isdone;
	}
	public boolean isIsok() {
		return isok;
	}
	public void setIsok(boolean isok) {
		this.isok = isok;
	}
	public double getPercent() {
		return percent;
	}
	public void setPercent(double percent) {
		this.percent = percent;
	}
	public DownLoadState(String filename, boolean isdone, boolean isok,
			double percent) {
		super();
		this.filename = filename;
		this.isdone = isdone;
		this.isok = isok;
		this.percent = percent;
	}
	public DownLoadState() {
		super();
		// TODO Auto-generated constructor stub
	}
	//���ȡֵ�������ظ����øö��󣬼�����һ�������̡߳�
	public void clear(){
		percent=0.0;
		filename=null;
		isdone=false;
		isok=false;
	}
	//��������ʡ�
	public void IncreasePercent(double delta){
		percent+=(delta*100.0);
		
		DecimalFormat df = new DecimalFormat("##.##");
		percent=Double.parseDouble(df.format(percent));
	}
}
