/**
 * Most of the code in the Qalingo project is copyrighted Hoteia and licensed
 * under the Apache License Version 2.0 (release version 0.8.0)
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *                   Copyright (c) Hoteia, 2012-2014
 * http://www.hoteia.com - http://twitter.com/hoteia - contact@hoteia.com
 *
 */
package org.hoteia.qalingo.app.business.job.email;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hoteia.qalingo.core.batch.CommonProcessIndicatorItemWrapper;
import org.hoteia.qalingo.core.domain.Email;
import org.hoteia.qalingo.core.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ReaderNotOpenException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.util.Assert;

/**
 * Thread-safe database {@link ItemReader} implementing the process indicator
 * pattern.
 * 
 */
public abstract class AbstractEmailItemReader<T> implements ItemReader<CommonProcessIndicatorItemWrapper<Long, Email>>, StepExecutionListener, InitializingBean, DisposableBean {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final Object lock = new Object();

	protected volatile boolean initialized = false;

	protected volatile Iterator<Long> keysIterator;

	protected EmailService emailService;
	
	public void destroy() {
		initialized = false;
		keysIterator = null;
	}

	public final void afterPropertiesSet() throws Exception {
		Assert.notNull(emailService, "You must provide an EmailService.");
	}
	
	public CommonProcessIndicatorItemWrapper<Long, Email> read() throws DataAccessException {

		if (!initialized) {
			throw new ReaderNotOpenException("Reader must be open before it can be used.");
		}

		Long key = null;
		synchronized (lock) {
			if (keysIterator.hasNext()) {
				key = keysIterator.next();
			}
		}
		logger.debug("Retrieved key from list: " + key);

		if (key == null) {
			return null;
		}
		Email result = null;
		try {
			result = emailService.getEmailById(key);
			
		} catch (Exception e) {
			logger.error("Fail to load", e);
			throw new ReaderNotOpenException("Fail to load");
		}
		
		return new CommonProcessIndicatorItemWrapper<Long, Email>(key, result);
	}
	
	public ExitStatus afterStep(StepExecution stepExecution) {
		
		destroy();
		
		return stepExecution.getExitStatus();
	}

	public void beforeStep(StepExecution stepExecution) {
		synchronized (lock) {
			if (keysIterator == null) {
				List<Long> keys = retrieveKeys();
				if(keys == null){
					keys = new ArrayList<Long>();
				}
				keysIterator = keys.iterator();
				logger.info("Keys obtained for staging.");
				initialized = true;
			}
		}
	}

	abstract protected List<Long> retrieveKeys() ;
	
	public void setEmailService(EmailService emailService) {
	    this.emailService = emailService;
    }

}