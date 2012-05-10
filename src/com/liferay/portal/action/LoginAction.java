/**
 * Copyright (c) 2000-2005 Liferay, LLC. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.action;

import java.util.List;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.PageContext;

import org.apache.struts.Globals;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.Layout;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.NoSuchUserException;
import com.liferay.portal.RequiredLayoutException;
import com.liferay.portal.SendPasswordException;
import com.liferay.portal.UserEmailAddressException;
import com.liferay.portal.UserIdException;
import com.liferay.portal.UserPasswordException;
import com.liferay.portal.auth.AuthException;
import com.liferay.portal.auth.Authenticator;
import com.liferay.portal.auth.PrincipalFinder;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import com.liferay.portal.util.Constants;
import com.liferay.portal.util.CookieKeys;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.WebKeys;
import com.liferay.util.InstancePool;
import com.liferay.util.ParamUtil;
import com.liferay.util.Validator;
import com.liferay.util.servlet.SessionErrors;
import com.liferay.util.servlet.SessionMessages;

/**
 * <a href="LoginAction.java.html"><b><i>View Source</i></b></a>
 *
 * @author  Brian Wing Shun Chan
 * @version $Revision: 1.4 $
 *
 */
public class LoginAction extends Action {

	public ActionForward execute(
			ActionMapping mapping, ActionForm form, HttpServletRequest req,
			HttpServletResponse res)
		throws Exception {

		HttpSession ses = req.getSession();

		String cmd = req.getParameter("my_account_cmd");

		if ((cmd != null) && (cmd.equals("auth"))) {
			try {
				_login(req, res);
				
				User user = APILocator.getUserAPI().loadUserById(((String) ses.getAttribute(WebKeys.USER_ID)), APILocator.getUserAPI().getSystemUser(), true);
				List<Layout> userLayouts = APILocator.getLayoutAPI().loadLayoutsForUser(user);
				if ((userLayouts == null) || (userLayouts.size() == 0) || !UtilMethods.isSet(userLayouts.get(0).getId())) {
					new LogoutAction().execute(mapping, form, req, res);
					throw new RequiredLayoutException();
				}
				

				// Touch protected resource

				return mapping.findForward("/portal/touch_protected.jsp");
				
				
				
				
				
			}
			catch (Exception e) {
				if (e != null &&
					e instanceof AuthException ||
					e instanceof NoSuchUserException ||
					e instanceof UserEmailAddressException ||
					e instanceof UserIdException ||
					e instanceof UserPasswordException ||
					e instanceof RequiredLayoutException) {

					SessionErrors.add(req, e.getClass().getName());

					return mapping.findForward("portal.login");
				}
				else {
					req.setAttribute(PageContext.EXCEPTION, e);

					return mapping.findForward(Constants.COMMON_ERROR);
				}
			}
		}
		else if ((cmd != null) && (cmd.equals("send"))) {
			try {
				_sendPassword(req);

				return mapping.findForward("portal.login");
			}
			catch (Exception e) {
				if (e != null &&
					e instanceof NoSuchUserException ||
					e instanceof SendPasswordException ||
					e instanceof UserEmailAddressException) {

					SessionErrors.add(req, e.getClass().getName());

					return mapping.findForward("portal.login");
				}
				else {
					req.setAttribute(PageContext.EXCEPTION, e);

					return mapping.findForward(Constants.COMMON_ERROR);
				}
			}
		}
		else {
			return mapping.findForward("portal.login");
		}
	}

	private void _login(HttpServletRequest req, HttpServletResponse res)
		throws Exception {

		HttpSession ses = req.getSession();

		String login = ParamUtil.getString(req, "my_account_login").toLowerCase();
		


		String password = ParamUtil.getString(req, "password");
		if (Validator.isNull(password)) {
			password = ParamUtil.getString(req, "password");
		}

		
		boolean rememberMe = ParamUtil.get(req, "my_account_r_m", false);

		String userId = login;

		int authResult = Authenticator.FAILURE;

		Company company = PortalUtil.getCompany(req);

		if (company.getAuthType().equals(Company.AUTH_TYPE_EA)) {
			authResult = APILocator.getUserAPI().authenticate(login, password);

//			userId = APILocator.getUserAPI().loadUserById(login, APILocator.getUserAPI().getSystemUser(), true).getUserId();
		}
		else {
			authResult = APILocator.getUserAPI().authenticate(login, password);
		}

		try {
			PrincipalFinder principalFinder =
				(PrincipalFinder)InstancePool.get(
					PropsUtil.get(PropsUtil.PRINCIPAL_FINDER));

			userId = principalFinder.fromLiferay(userId);
		}
		catch (Exception e) {
		}

		if (authResult == Authenticator.SUCCESS) {
			User user = APILocator.getUserAPI().loadUserById(userId,APILocator.getUserAPI().getSystemUser(),true);
			
			//DOTCMS-4943
			UserAPI userAPI = APILocator.getUserAPI();			
			boolean respectFrontend = WebAPILocator.getUserWebAPI().isLoggedToBackend(req);			
			Locale userSelectedLocale = (Locale)req.getSession().getAttribute(Globals.LOCALE_KEY);			
			user.setLanguageId(userSelectedLocale.toString());
			userAPI.save(user, userAPI.getSystemUser(), respectFrontend);

			ses.setAttribute(WebKeys.USER_ID, userId);
			
			//set the host to the domain of the URL if possible if not use the default host
			//http://jira.dotmarketing.net/browse/DOTCMS-4475
			try{
				String domainName = req.getServerName();
				Host h = null;
				h = APILocator.getHostAPI().findByName(domainName, user, false);
				if(h == null || !UtilMethods.isSet(h.getInode())){
					h = APILocator.getHostAPI().findByAlias(domainName, user, false);
				}
				if(h != null && UtilMethods.isSet(h.getInode())){
					req.getSession().setAttribute(com.dotmarketing.util.WebKeys.CMS_SELECTED_HOST_ID, h.getIdentifier());
				}else{
					req.getSession().setAttribute(com.dotmarketing.util.WebKeys.CMS_SELECTED_HOST_ID, APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), true).getIdentifier());
				}
			}catch (DotSecurityException se) {
				req.getSession().setAttribute(com.dotmarketing.util.WebKeys.CMS_SELECTED_HOST_ID, APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), true).getIdentifier());
			}
						
			ses.removeAttribute("_failedLoginName");
			Cookie idCookie = new Cookie(CookieKeys.ID,APILocator.getUserAPI().encryptUserId(userId));
			idCookie.setPath("/");

			
			if (rememberMe) {
				idCookie.setMaxAge(31536000);
			}
			else {
				idCookie.setMaxAge(0);
			}
			


			res.addCookie(idCookie);

		}

		if (authResult != Authenticator.SUCCESS) {
			Logger.info(this, "An ivalid attempt to login as " + login + " has been made from IP: " + req.getRemoteAddr());
			throw new AuthException();
		}
		
		Logger.info(this, "User " + login + " has sucessfully login from IP: " + req.getRemoteAddr());
	}

	private void _sendPassword(HttpServletRequest req) throws Exception {
		String emailAddress = ParamUtil.getString(
			req, "my_account_email_address");

		APILocator.getUserAPI().sendPassword(APILocator.getUserAPI().loadByUserByEmail(emailAddress, APILocator.getUserAPI().getSystemUser(), true));

		Logger.info(this, "Email address " + emailAddress + " has request to reset his password from IP: " + req.getRemoteAddr());

		SessionMessages.add(req, "new_password_sent", emailAddress);
	}

}
