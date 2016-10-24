package work.generic.util.error_handler;

import lombok.Getter;
import lombok.Setter;
import work.WorkMain;
import work.generic.AbstractWorkMain;
import work.generic.datamodel.access.WorkUser;
import work.generic.datamodel.employee.WorkEmployee;
import work.generic.module.AbstractWorkModule;
import work.generic.util.StringUtil;
import work.generic.util.WorkProperty;

public class WorkError
{

	@Getter
	private String					userName;
	@Setter
	@Getter
	private String					occurenceTime;
	@Setter
	@Getter
	private String					serverName;
	@Setter
	@Getter
	private String					errorDetails;
	@Getter
	@Setter
	private String					stackTrace;

	public void setUsername() throws Exception
	{
		StringBuilder sb = new StringBuilder();

		AbstractWorkMain work = WorkMain.getInstance();
		WorkUser actingUser = work.getActingUser();
		WorkUser loggedInUser = work.getLoggedInUser();
		WorkEmployee actingEmployee = work.getActingEmployee();
		AbstractWorkModule focusedModule = work.getFocusedModule();

		if (actingUser != null)
		{
			if (actingUser.getWorkAddressPerson() != null)
				sb.append(StringUtil.format("acting user: %s%s", actingUser.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
			else
				sb.append(StringUtil.format("acting user: %s%s", actingUser, System.lineSeparator()));
		}
		if (loggedInUser != null)
		{
			if (loggedInUser.getWorkAddressPerson() != null)
				sb.append(StringUtil.format("logged in user: %s%s", loggedInUser.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
			else
				sb.append(StringUtil.format("logged in user: %s%s", loggedInUser, System.lineSeparator()));
		}
		if (actingEmployee != null)
		{
			if (actingEmployee.getWorkAddressPerson() != null)
				sb.append(StringUtil.format("acting employee: %s%s", actingEmployee.getWorkAddressPerson().getFullNameAndId(), System.lineSeparator()));
			else
				sb.append(StringUtil.format("acting employee: %s%s", actingEmployee, System.lineSeparator()));
		}
		if (focusedModule != null)
			sb.append(StringUtil.format("module: %s%s", focusedModule.getDescriptor().getModuleName(), System.lineSeparator()));

		sb.append(StringUtil.format("hostname: %s%s", WorkProperty.HOSTNAME_DOMAIN.getValue(), System.lineSeparator()));
		sb.append(StringUtil.format("ip-address: %s%s", WorkProperty.HTTP_HOST.getValue(), System.lineSeparator()));

		userName = sb.toString();
	}
}