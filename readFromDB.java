@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    String urlPath = request.getPathInfo();
    if (urlPath == null || urlPath.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().write("missing parameters");
        return;
    }

    String[] urlParts = urlPath.split("/");
    if (!isUrlValid(urlParts)) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    } else {
        if (urlPath.length() == 7 && urlParts[6].equals("skiers")) {
            try (Jedis jedis = jedisPool.getResource()) {
                response.getWriter().write(String.valueOf(
                jedis.scard("resortId:" + urlParts[1] + "seasonId:" + urlParts[3] + "dayId:" + urlParts[5])));
             }
        } else if (urlPath.length() == 8) {
            try (Jedis jedis = jedisPool.getResource()) {
                response.getWriter().write(jedis.get("resortId:" + urlParts[1] + "seasonId:" + urlParts[3] +
                    "dayId:" + urlParts[5] + "skierId:" + urlParts[7] + "vertical:"));
            }
        } else if (urlPath.length() == 5) {
            try (Jedis jedis = jedisPool.getResource()) {
                response.getWriter().write(jedis.get("resortId:" + urlParts[3] + "skierId:" + urlParts[1] + "vertical:"));
            }
        } else if (urlPath.length() == 3) {
            try (Jedis jedis = jedisPool.getResource()) {
                response.getWriter().write(jedis.get("skierId:" + urlParts[1] + "vertical:"));
            }
        } else if (urlPath.length() == 7 && urlParts[6].equals("vertical")) {
            try (Jedis jedis = jedisPool.getResource()) {
                response.getWriter().write(jedis.get("resortId:" + urlParts[3] + "seasonId:" + urlParts[5] +
                    "skierId:" + urlParts[1] + "vertical:"));
            }
        }
    }
}
