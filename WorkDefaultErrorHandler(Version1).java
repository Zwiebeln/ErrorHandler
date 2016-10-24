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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import rx.subjects.PublishSubject;
import work.generic.registry.RegistryEnum;
import work.generic.services.ServiceRegistry;
import work.generic.services.mail.WorkEmail;
import work.generic.util.EnvironmentUtil;

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
	
	@Getter
	@AllArgsConstructor	
	static class XYZ {
		Throwable t; 
	}

	private static final long					serialVersionUID	= -9098079721078047349L;

	private static final Logger					stdoutLog			= Logger.getLogger("stdout");
	private static final Logger					stderrLog			= Logger.getLogger("stderr");
	private Queue<WorkError>					errorQueue			= new LinkedList<WorkError>();

	@Getter
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

		if (EnvironmentUtil.isDebug())
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

		WorkError workError = new WorkError();
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

		try
		{
			workError.setUsername();
		}
		catch (Exception e)
		{
			log.error("additional Information error", e);
		}
		workError.setOccurenceTime(getCurrentDateTime().toString());
		workError.setErrorDetails(event.getThrowable().getMessage());
		workError.setStackTrace(getStackTrace(t));

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

	private void sendErrorEmail(List<Throwable> t)
	{
		Preconditions.checkNotNull(t);
		Preconditions.checkArgument(!t.isEmpty());

		String developerEmail = RegistryEnum.DeveloperExceptionEmailRecipient.getValue();
		String subject = String.format("Exception in Anwendung \"%s\" am %s", RegistryEnum.Work_ApplicationName.getValue(), getCurrentDateTime());

		StringBuilder message = new StringBuilder();
		int errorNr = 1;
		while (!errorQueue.isEmpty())
		{
			message.append("\n Der " + errorNr + " Fehler: \n");
			errorNr++;
			message.append(System.lineSeparator());
			message.append("User: " + errorQueue.peek().getUserName());
			message.append("Datum und Uhrzeit: " + errorQueue.peek().getOccurenceTime());
			message.append(System.lineSeparator());
			message.append("Fehlermeldung: " + errorQueue.peek().getErrorDetails());
			message.append(System.lineSeparator());
			message.append("Stack trace: " + errorQueue.remove().getStackTrace());
			message.append("------------------------------------------------------------------------------------------------");
			message.append(System.lineSeparator());
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
