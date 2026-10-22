<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- SecurityFilter sends visitors who are not signed in on to the login page --%>
<c:redirect url="/DashboardController"/>
