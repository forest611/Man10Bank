FROM node:18.12.1-alpine
WORKDIR /front-end
ENV PATH /front-end/node_modules/.bin:$PATH
COPY package.json ./
COPY package-lock.json ./
RUN npm i
COPY . ./
EXPOSE 3000
CMD ["npm", "start"]