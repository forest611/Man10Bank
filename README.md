# Man10Bank

Minecraftの経済鯖を運営する上で使用する銀行システム

- Man10Bank : Paper鯖に導入するプラグイン
- Man10BankServer : C# ASP.netのRESTFul WebAPI鯖

## Paperプラグインから銀行にアクセスするための依存設定

[![](https://jitpack.io/v/forest611/Man10Bank.svg)](https://jitpack.io/#forest611/Man10Bank)

	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url 'https://jitpack.io' }
		}
	}

	dependencies {
	        implementation 'com.github.forest611:Man10Bank:Tag'
	}

## API仕様書

swaggerで見てください

## フロントエンドアプリ(開発中)

https://github.com/forest611/Man10BankFront

