package sjsu.cmpe.cache.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.json.JSONObject;

public class CRDTClient {
	public static void put(int key, String value)
	{
		final CountDownLatch latch1 = new CountDownLatch(3);
		CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
		httpclient.start();	
		try {		
			for(int i=0;i<3;i++)
			{
				final HttpPut req = new HttpPut("http://localhost:300"+i+"/cache/"+key+"/"+value+"");
				httpclient.execute(req, new FutureCallback<HttpResponse>() {
					public void completed(final HttpResponse resp) {
						latch1.countDown();
						System.out.println(req.getRequestLine() + "->" + resp.getStatusLine());
						String b="";
						try {
							b = IOUtils.toString(resp.getEntity().getContent());
						} catch (IOException e) {
							// TODO: handle exception
							e.printStackTrace();
						}
						System.out.println(b);
					}

					public void failed(final Exception ex) {

						System.out.println(req.getRequestLine() + "->" + ex);
					}

					public void cancelled() {
						latch1.countDown();
						System.out.println(req.getRequestLine() + " cancelled");
					}
				});

			}
			latch1.await(2000,TimeUnit.MILLISECONDS);
			httpclient.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		finally
		{
			try
			{

				latch1.await(2000,TimeUnit.MILLISECONDS);
				httpclient.close();
			}
			catch (Exception e2) {
				// TODO: handle exception
			}
			if (latch1.getCount()>1)
			{
				System.out.println("Do Repair");
				System.out.println("Write Failed");
				remove(key);
			}
		}
	}


	public static void remove(int key)
	{
		final CountDownLatch latch1 = new CountDownLatch(3);
		CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
		httpclient.start();	
		try {
			for(int i=0;i<3;i++)
			{
				final HttpDelete req = new HttpDelete("http://localhost:300"+i+"/cache/"+key);

				httpclient.execute(req, new FutureCallback<HttpResponse>() {
					public void completed(final HttpResponse resp) {
						latch1.countDown();
						System.out.println(req.getRequestLine() + "->" + resp.getStatusLine());
						String b="";
						try {
							b = IOUtils.toString(resp.getEntity().getContent());							
						} catch (IOException e) {
							// TODO: handle exception
							e.printStackTrace();
						}
					}

					public void failed(final Exception ex) {

						System.out.println(req.getRequestLine() + "->" + ex);
					}

					public void cancelled() {
						latch1.countDown();
						System.out.println(req.getRequestLine() + " Cancelled");
					}
				});

			}
			latch1.await(2000,TimeUnit.MILLISECONDS);
			httpclient.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		finally
		{
			try
			{
				latch1.await(2000,TimeUnit.MILLISECONDS);
				httpclient.close();

			} catch (Exception e2) {
				// TODO: handle exception
			}
		}
	}

	public static ArrayList<String> l=new ArrayList<String>();
	public static void increase(String value)
	{
		if(value!=null)
		{
			l.add(value);
		}
	}
	public static void get(int key)
	{
		final CountDownLatch latch1 = new CountDownLatch(3);
		CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
		httpclient.start();	
		int size=0;
		try {
			for(int i=0;i<3;i++)
			{
				final HttpGet req = new HttpGet("http://localhost:300"+i+"/cache/"+key);

				Future<HttpResponse>resp =httpclient.execute(req, new FutureCallback<HttpResponse>() {
					public void completed(final HttpResponse resp) {
						latch1.countDown();
						System.out.println(req.getRequestLine() + "->" + resp.getStatusLine());
						String body="";
						try 
						{
							body = IOUtils.toString(resp.getEntity().getContent());	
							JSONObject json=new JSONObject(body);
							increase(json.getString("value"));
						} catch (IOException e) {
							// TODO: handle exception

							e.printStackTrace();
						}
						JSONObject json=new JSONObject(body);
						System.out.println(body);
						l.add(json.getString("value"));
					}
					public void failed(final Exception ex) {
						System.out.println(req.getRequestLine() + "->" + ex);
					}
					public void cancelled() {
						System.out.println(req.getRequestLine() + " Cancelled");
					}
				});	
			}	
			
			latch1.await(2000,TimeUnit.MILLISECONDS);
			Thread.sleep(2100);
			size=l.size();
			//Read Repair
			String temp=majority();
			if(!temp.equals("None"))
			{
				if(size<2)
				{
					l=new ArrayList<String>();
					System.out.println("Majority Servers Not Running...");
					remove(key);
					return;
				}
					System.out.println("Repair Operation");
					remove(key);
					put(key,temp);
			}				
			else
			{
				System.out.println("Data Is Consistent");
			}
				httpclient.close();
			} catch (Exception e) {
				// TODO: handle exception
			}
			finally
			{
				try
				{
					Thread.sleep(2100);
					//Read Repair
					size=l.size();
					String temp=majority();
						if(!temp.equals("None"))
						{
							if(size<2)
							{
								l=new ArrayList<String>();
								System.out.println("Majority Servers Not Running...");
								remove(key);
								return;
							}
							System.out.println("Performing Read Repair....");
							remove(key);
							put(key,temp);
						}	
						else
						{
							
							System.out.println("Data Is Consistent");
						}
					latch1.await(2000,TimeUnit.MILLISECONDS);
					httpclient.close();
				} catch (Exception e2) {
					// TODO: handle exception
				}

			}
		}
		public static String majority()
		{
			Map<String, Integer> stringsCount = new HashMap<String, Integer>();
			
			if(l.size()==0)
			{
				return "None";
			}
			for(String s: l)
			{
				Integer c = stringsCount.get(s);
				if(c == null) c = new Integer(0);
				c++;
				stringsCount.put(s,c);
			}
			Map.Entry<String,Integer> mostRepeated = null;
			for(Map.Entry<String, Integer> e: stringsCount.entrySet())
			{
				if(mostRepeated == null || mostRepeated.getValue()<e.getValue())
					mostRepeated = e;
			}
			if(mostRepeated != null)
			{
				System.out.println("Majority Value Is: " + mostRepeated.getKey());		
				if(mostRepeated.getValue()==3)
				{
					l=new ArrayList<String>();
					return "None";
				}
			}
			l=new ArrayList<String>();
			return mostRepeated.getKey();
		} 
}
