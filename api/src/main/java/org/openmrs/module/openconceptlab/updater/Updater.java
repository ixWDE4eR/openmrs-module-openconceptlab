package org.openmrs.module.openconceptlab.updater;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.module.openconceptlab.Item;
import org.openmrs.module.openconceptlab.State;
import org.openmrs.module.openconceptlab.Subscription;
import org.openmrs.module.openconceptlab.Update;
import org.openmrs.module.openconceptlab.UpdateService;
import org.openmrs.module.openconceptlab.client.OclClient;
import org.openmrs.module.openconceptlab.client.OclClient.OclResponse;
import org.openmrs.module.openconceptlab.client.OclConcept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("openconceptlab.updater")
public class Updater implements Runnable {
	
	@Autowired
	UpdateService updateService;
	
	@Autowired
	OclClient oclClient;
	
	@Autowired
	Importer importer;
	
	private CountingInputStream in = null;
	
	private volatile long totalBytesToProcess;
			
	/**
	 * @should start first update with response date
	 * @should start next update with updated since
	 * @should create item for each concept
	 */
	@Override
	public void run() {		
		Subscription subscription = updateService.getSubscription();
		Update lastUpdate = updateService.getLastSuccessfulUpdate();
		Date updatedSince = null;
		if (lastUpdate != null) {
			updatedSince = lastUpdate.getOclDateStarted();
		}
		
		Update update = new Update();
		updateService.startUpdate(update);
		
		OclResponse oclResponse;
		try {
			oclResponse = oclClient.fetchUpdates(subscription.getUrl(), subscription.getToken(), updatedSince);
		}
		catch (Exception e) {
			update.setErrorMessage(getErrorMessage(e));
			updateService.stopUpdate(update);
			throw new ImportException(e);
		}
		
		updateService.updateOclDateStarted(update, oclResponse.getUpdatedTo());
		
		in = new CountingInputStream(oclResponse.getContentStream());
		totalBytesToProcess = oclResponse.getContentLength();
		try {
			process(update, in);
			in.close();
		}
		catch (Exception e) {
			update.setErrorMessage(getErrorMessage(e));
			throw new ImportException(e);
		}
		finally {
			IOUtils.closeQuietly(in);
			updateService.stopUpdate(update);
		}
	}

	private String getErrorMessage(Exception e) {
	    String message = "Failed with '" + e.getMessage() + "'";
	    Throwable rootCause = ExceptionUtils.getRootCause(e);
	    if (rootCause != null) {
	    	String[] stackFrames = ExceptionUtils.getStackFrames(rootCause);
	    	int endIndex = stackFrames.length > 5 ? 5 : stackFrames.length;
	    	message += " caused by: " + StringUtils.join(stackFrames, "\n", 0, endIndex);
	    }
	    if (message.length() > 1024) {
	    	return message.substring(0, 1024);
	    } else {
	    	return message;
	    }
    }
	
	public long getBytesDownloaded() {
		return oclClient.getBytesDownloaded();
	}
	
	public long getTotalBytesToDownload() {
		return oclClient.getTotalBytesToDownload();
	}
	
	public boolean isDownloaded() {
		return oclClient.isDownloaded();
	}
	
	public long getBytesProcessed() {
		if (in != null) {
			return in.getByteCount();
		} else {
			return 0;
		}
	}
	
	public long getTotalBytesToProcess() {
		return totalBytesToProcess;
	}
	
	void process(Update update, InputStream in) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonParser parser = objectMapper.getJsonFactory().createJsonParser(in);
		
		JsonToken token = parser.nextToken();
		if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY) {
			throw new IOException("JSON must start from an object or an array");
		}
		if (token == JsonToken.START_OBJECT) {
			token = parser.nextToken();
		}
		
		if (token == JsonToken.END_OBJECT) {
			return;
		}
		if (token != JsonToken.START_ARRAY) {
			throw new IOException("JSON must have a list of concepts or an empty list");
		}
		
		ImportQueue importQueue = new ImportQueue();
		
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			OclConcept oclConcept = parser.readValueAs(OclConcept.class);
			
			importQueue.offer(oclConcept);
			
			while (!importQueue.isEmpty()) {
				Item item = null;
				try {
					oclConcept = importQueue.peek();
					item = importer.importConcept(update, importQueue);
				}
				catch (ImportException e) {
					item = new Item(update, oclConcept, State.ERROR);
					item.setErrorMessage(getErrorMessage(e));
				} finally {
					if (!State.MISSING_DEPENDENCY.equals(item.getState())) {
						updateService.saveItem(item);
					}
				}
			}
		}
	}
	
}
