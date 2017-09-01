/**
 * 
 */
package jazmin.deploy.workflow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import jazmin.deploy.workflow.definition.TaskTemplate;
import jazmin.deploy.workflow.definition.WorkflowProcess;
import jazmin.deploy.workflow.execute.Execute;
import jazmin.deploy.workflow.execute.JavaScriptClassExecute;
import jazmin.deploy.workflow.execute.ProcessInstance;
import jazmin.log.Logger;
import jazmin.log.LoggerFactory;
import jazmin.util.FileUtil;
import jazmin.util.JSONUtil;

/**
 * @author yama
 *
 */
public class WorkflowEngine {
	private static Logger logger=LoggerFactory.get(WorkflowEngine.class);
	//
	public static interface ExecuteFactory{
		public Execute getExecute(String name);
	}
	//
	public static interface ThreadPool{
		public void run(Runnable runnable);
	}
	//----------------------------------------------------------------------
	public static class JavaClassExecuteFactory implements ExecuteFactory{
		@SuppressWarnings("unchecked")
		@Override
		public Execute getExecute(String name) {
			try {
				Class<?> cz= (Class<? extends Execute>) Class.forName(name);
				return (Execute) cz.newInstance();
			} catch (Exception e) {
				logger.catching(e);
				return null;
			}
		}
	}
	//
	public static class JavaScriptExecuteFactory implements ExecuteFactory{
		//
		@Override
		public Execute getExecute(String name) {
			JavaScriptClassExecute e=new JavaScriptClassExecute(name);
			return e;
		}
	}
	//
	public static class JavaScriptFileExecuteFactory implements ExecuteFactory{
		public JavaScriptFileExecuteFactory() {
			scriptSourceProvider=new ScriptSourceProvider() {
				@Override
				public String getSource(String name) {
					File file=new File(name);
					if(file.exists()){
						try {
							return FileUtil.getContent(file);
						} catch (IOException e) {
							logger.catching(e);
						}
					}
					return null;
				}
			};
		}
		//
		@Override
		public Execute getExecute(String name) {
			String source=scriptSourceProvider.getSource(name);
			if(source==null){
				return null;
			}
			JavaScriptClassExecute e=new JavaScriptClassExecute(source);
			return e;
		}
		//
		ScriptSourceProvider scriptSourceProvider;
		/**
		 * @return the scriptSourceProvider
		 */
		public ScriptSourceProvider getScriptSourceProvider() {
			return scriptSourceProvider;
		}
		/**
		 * @param scriptSourceProvider the scriptSourceProvider to set
		 */
		public void setScriptSourceProvider(ScriptSourceProvider scriptSourceProvider) {
			this.scriptSourceProvider = scriptSourceProvider;
		}
		
	}
	//
	public static interface ScriptSourceProvider{
		String getSource(String name);
	}
	//
	public static class DefaultThreadPool implements ThreadPool{
		ScheduledExecutorService scheduledExecutorService=
				new ScheduledThreadPoolExecutor(
						5,
						new DefaultThreadFactory("WorkflowEngine"),
						new ThreadPoolExecutor.AbortPolicy());
		//
		@Override
		public void run(Runnable runnable) {
			scheduledExecutorService.execute(runnable);
		}
	}
	//----------------------------------------------------------------------
	//
	//
	Map<String,ExecuteFactory>executeFactorys;
	ThreadPool threadPool;
	//
	List<WorkflowEventListener>eventListeners;
	Map<String,TaskTemplate> taskTemplateMap;
	//
	public WorkflowEngine() {
		executeFactorys=new HashMap<>();
		threadPool=new DefaultThreadPool();
		executeFactorys.put("java", new JavaClassExecuteFactory());
		executeFactorys.put("jsfile",new JavaScriptFileExecuteFactory());
		executeFactorys.put("js",new JavaScriptExecuteFactory());
		eventListeners=new ArrayList<>();
		taskTemplateMap=new LinkedHashMap<>();
	}
	//
	public void registerTaskTemplate(TaskTemplate t){
		taskTemplateMap.put(t.id,t);
	}
	//
	public List<TaskTemplate>getTaskTemplates(){
		return new ArrayList<>(taskTemplateMap.values());
	}
	//
	public ExecuteFactory getExecuteFactory(String id){
		return executeFactorys.get(id);
	}
	//
	public void registerEventListener(WorkflowEventListener l){
		eventListeners.add(l);
	}
	//
	public void removeEventListener(WorkflowEventListener l){
		eventListeners.remove(l);
	}
	//
	public void fireEvent(WorkflowEvent e){
		for(WorkflowEventListener l:eventListeners){
			threadPool.run(()->{
				try{
					l.actionPerformed(e);
				}catch (Exception ee) {
					logger.catching(ee);
				}
			});
		}
	}
	/**
	 * 
	 */
	public void registerExecuteFactory(String id,ExecuteFactory factory){
		if(executeFactorys.containsKey(id)){
			throw new IllegalArgumentException("execute factory "+id+" already exists");
		}
		executeFactorys.put(id, factory);
	}
	/**
	 * 
	 * @param execute
	 * @return
	 */
	public Execute loadExecute(String execute){
		int t=execute.indexOf(":");
		if(t==-1){
			return null;
		}
		String type=execute.substring(0,t).trim();
		String value=execute.substring(t+1).trim();
		ExecuteFactory factory=executeFactorys.get(type);
		if(factory!=null){
			return factory.getExecute(value);
		}
		return null;
	}
	//
	public WorkflowProcess loadProcess(String json){
		return JSONUtil.fromJson(json, WorkflowProcess.class);
	}
	//
	public ProcessInstance startProcess(WorkflowProcess process){
		ProcessInstance instance=new ProcessInstance(process,this);
		instance.start();
		return instance;
	}
	//
	/**
	 * @param threadPool the threadPool to set
	 */
	public void setThreadPool(ThreadPool threadPool) {
		this.threadPool = threadPool;
	}
}
