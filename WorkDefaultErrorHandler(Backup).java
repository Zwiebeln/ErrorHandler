package work.generic.util.error_handler;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.eclipse.jetty.io.EofException;

import com.google.common.base.Preconditions;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.server.DefaultErrorHandler;
import com.vaadin.server.ErrorEvent;
import com.vaadin.server.ErrorHandler;
import com.vaadin.ui.UI;

import lombok.extern.log4j.Log4j;
import rx.subjects.PublishSubject;
import work.WorkMain;
import work.generic.AbstractWorkMain;
import work.generic.datamodel.access.WorkUser;
import work.generic.datamodel.employee.WorkEmployee;
import work.generic.module.AbstractWorkModule;
import work.generic.registry.RegistryEnum;
import work.generic.services.ServiceRegistry;
import work.generic.services.mail.WorkEmail;
import work.generic.util.EnvironmentUtil;
import work.generic.util.StringUtil;
import work.generic.util.WorkProperty;

/**
 * In case Work is running in a productive environment, send an email with the exception to the address configured in
 * RegistryEnum.DeveloperExceptionEmailRecipient. Else if it is a debug environment, don't send emails.
 * 
 * @author fschmitt
 *
 */
@Log4j
public class WorkDefaultErrorHandler extends DefaultErrorHandler
{

	private static final long					serialVersionUID	= -9098079721078047349L;

	private static final Logger					stdoutLog			= Logger.getLogger("stdout");
	private static final Logger					stderrLog			= Logger.getLogger("stderr");
	private Queue<WorkError> errorQueue;

	private transient PublishSubject<Throwable>	push				= PublishSubject.create();

	public WorkDefaultErrorHandler()
	{
		setupLoggingConfig();
		setupPushListener();
	}

	private long getBufferTime()
	{
		return 5;
	}

	private TimeUnit getBufferTimeUnit()
	{
		return TimeUnit.MINUTES;
	}

	private void setupPushListener()
	{
		// instantly log to STDOUT Logger
		push.subscribe(x -> log.error("", x));

		if (!EnvironmentUtil.isDebug())
		{
			// buffer throwables for $bufferTime $bufferTimeUnit and send email with all stacktraces
			push.retry()//
					.buffer(getBufferTime(), getBufferTimeUnit())//
					.filter(ex -> !ex.isEmpty())//
					.subscribe(ex -> {
						try
						{
							sendErrorEmail(ex);
						}
						catch (Exception e)
						{
							log.error("error sending error handler email", e);
						}
					});
		}
	}

	@Override
	public void error(com.vaadin.server.ErrorEvent event)
	{
		errorQueue = new LinkedList<WorkError>();
		WorkError workError= new WorkError();
		Throwable t = event.getThrowable();

		if (t instanceof SocketException)
		{
			// Most likely client browser closed socket
			log.info("SocketException in CommunicationManager. Most likely client (browser) closed socket.");
			return;
		}

		t = findRelevantThrowable(t);

		boolean doFilterThrowable = filterThrowable(t);
		if (!doFilterThrowable)
			push.onNext(t);
		
		workError.setOccurenceTime(getCurrentDateTime().toString());
		workError.setErrorDetails(event.getThrowable().getMessage());
		errorQueue.add(workError);
	}

	protected boolean filterThrowable(Throwable t)
	{
		// caused by a client unexpectedly closing a connection
		if (t instanceof EofException)
		{
			return true;
		}
		// vaadin validation exception
		else if (t instanceof InvalidValueException)
		{
			return true;
		}

		return false;
	}

	public void error(Throwable t)
	{
		push.onNext(t);
	}

	private String additionalInformation()
	{
		StringBuilder sb = new StringBuilder();

		try
		{
//			AbstractWorkMain work = WorkMain.getInstance();
//			WorkUser actingUser = work.getActingUser();
//			WorkUser loggedInUser = work.getLoggedInUser();
//			WorkEmployee actingEmployee = work.getActingEmployee();
//			AbstractWorkModule focusedModule = work.getFocusedModule();
//
//			if (actingUser != null)
//			{
//				if (actingUser.getWorkAddressPerson() != null)
//					sb.append(StringUtil.format("acting user: %s%s", actingUser.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
//				else
//					sb.append(StringUtil.format("acting user: %s%s", actingUser, System.lineSeparator()));
//			}
//			if (loggedInUser != null)
//			{
//				if (loggedInUser.getWorkAddressPerson() != null)
//					sb.append(StringUtil.format("logged in user: %s%s", loggedInUser.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
//				else
//					sb.append(StringUtil.format("logged in user: %s%s", loggedInUser, System.lineSeparator()));
//			}
//			if (actingEmployee != null)
//			{
//				if (actingEmployee.getWorkAddressPerson() != null)
//					sb.append(StringUtil.format("acting employee: %s%s", actingEmployee.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
//				else
//					sb.append(StringUtil.format("acting employee: %s%s", actingEmployee, System.lineSeparator()));
//			}
//			if (focusedModule != null)
//				sb.append(StringUtil.format("module: %s%s", focusedModule.getDescriptor().getModuleName(), System.lineSeparator()));
//
//			sb.append(StringUtil.format("hostname: %s%s", WorkProperty.HOSTNAME_DOMAIN.getValue(), System.lineSeparator()));
//			sb.append(StringUtil.format("ip-address: %s%s", WorkProperty.HTTP_HOST.getValue(), System.lineSeparator()));

		}
		catch (Exception e)
		{
			log.error("additional Information error", e);
		}
		return sb.toString();
	}

	private void sendErrorEmail(List<Throwable> t)
	{
		Preconditions.checkNotNull(t);
		Preconditions.checkArgument(!t.isEmpty());

		String developerEmail = RegistryEnum.DeveloperExceptionEmailRecipient.getValue();
		String subject = String.format("Exception in Anwendung \"%s\" am %s", RegistryEnum.Work_ApplicationName.getValue(), getCurrentDateTime());

//		StringBuilder sb = new StringBuilder();
//		Queue<String> errorQueue = new LinkedList<String>();
//
//		for (Throwable x : t)
//		{
//			String stackTrace = getStackTrace(x);
//			sb.append(stackTrace);
//			sb.append(System.lineSeparator());
//			errorQueue.add(sb.toString());
//		}
//
		StringBuilder message = new StringBuilder();
//		message.append(additionalInformation());
//		message.append(System.lineSeparator());
//		message.append("error time:  ");
//		message.append(getCurrentDateTime());
//		message.append(System.lineSeparator());
//		message.append(WorkMain.getWorkVersionString());
//		message.append(System.lineSeparator());
//		message.append("Stacktrace: ");
//		message.append(System.lineSeparator());
//
//		while (!errorQueue.isEmpty())
//		{
//			message.append(errorQueue.remove());
//			message.append(System.lineSeparator());
//		}
		
		
		while (!errorQueue.isEmpty()){
			message.append(errorQueue.peek().getUserName());
			message.append(System.lineSeparator());
			message.append(errorQueue.peek().getOccurenceTime());
			message.append(System.lineSeparator());
			message.append(errorQueue.peek().getErrorDetails());
			message.append("---------------------------------------------------------");
		}
		WorkEmail email = new WorkEmail(subject, message.toString());
		email.addEmailAddress(developerEmail);
		ServiceRegistry.getMailService().sendMail(email);
	}

	private String getCurrentDateTime()
	{
		Calendar cal = Calendar.getInstance();
		Date time = cal.getTime();
		DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		return formatter.format(time);
	}

	private String getStackTrace(Throwable t)
	{
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}

	protected void setupLoggingConfig()
	{
		System.setOut(createLoggingProxy(System.out, stdoutLog));
		System.setErr(createLoggingProxy(System.err, stderrLog));
	}

	private static PrintStream createLoggingProxy(final PrintStream realPrintStream, final Logger log)
	{

		return new PrintStream(realPrintStream)
		{

			private StringBuilder sb = new StringBuilder();

			@Override
			public void println(String string)
			{
				if (sb.length() == 0)
				{
					log.info(string);
				}
				else
				{
					log.info(sb.toString() + string);
					sb = new StringBuilder();
				}
			}

			@Override
			public void print(final String string)
			{
				sb.append(string);
			}

			@Override
			public void println(Object x)
			{
				if (x instanceof Exception)
				{
					Exception e = (Exception) x;
					log.error(e.getMessage(), e);
				}
				else
				{
					super.println(x);
				}
			}
		};
	}

	public static void handleError(Throwable e)
	{
		UI ui = UI.getCurrent();
		if (ui != null)
		{
			ErrorHandler errorHandler = ui.getErrorHandler();
			Preconditions.checkNotNull(errorHandler);
			errorHandler.error(new ErrorEvent(e));
		}
		else
		{
			WorkDefaultErrorHandler handler = new WorkDefaultErrorHandler();
			handler.error(new ErrorEvent(e));
		}
	}
}
