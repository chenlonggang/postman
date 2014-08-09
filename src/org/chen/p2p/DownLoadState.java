package org.chen.p2p;

import java.text.DecimalFormat;

/*
 * 这个类主要用来实现对下载状态的记录
 * 对应一个对具体文件的下载请求。
 * 
 * 在客户端使用这个类时，不要去用set**改变这些属性的取值，
 * 这些值会根据任务的类型，进展的情况自动设置、变更
 */
public class DownLoadState {

	private String filename=null;//监听的是那个文件的下载任务。
	private boolean isdone=false;//完成了吗。
	private boolean isok=false;//正确完成了吗。
	private double percent=0.0;//完成的百分比。
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
	//清空取值，可以重复利用该对象，监听下一个下载线程。
	public void clear(){
		percent=0.0;
		filename=null;
		isdone=false;
		isok=false;
	}
	//增大完成率。
	public void IncreasePercent(double delta){
		percent+=(delta*100.0);
		
		DecimalFormat df = new DecimalFormat("##.##");
		percent=Double.parseDouble(df.format(percent));
	}
}
