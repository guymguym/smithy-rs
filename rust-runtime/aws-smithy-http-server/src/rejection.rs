/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

use crate::response::IntoResponse;
use thiserror::Error;

// This is used across different protocol-specific `rejection` modules.
#[derive(Debug, Error)]
pub enum MissingContentTypeReason {
    #[error("headers taken by another extractor")]
    HeadersTakenByAnotherExtractor,
    #[error("no `Content-Type` header")]
    NoContentTypeHeader,
    #[error("`Content-Type` header value is not a valid HTTP header value: {0}")]
    ToStrError(http::header::ToStrError),
    #[error("invalid `Content-Type` header value mime type: {0}")]
    MimeParseError(mime::FromStrError),
    #[error("unexpected `Content-Type` header value; expected {expected_mime:?}, found {found_mime:?}")]
    UnexpectedMimeType {
        expected_mime: Option<mime::Mime>,
        found_mime: Option<mime::Mime>,
    },
}

pub mod any_rejections {
    //! This module hosts enums, up to size 8, which implement [`IntoResponse`] when their variants implement
    //! [`IntoResponse`].

    use super::IntoResponse;

    macro_rules! any_rejection {
        ($name:ident, $($var:ident),+) => (
            pub enum $name<$($var),*> {
                $($var ($var),)*
            }

            impl<P, $($var,)*> IntoResponse<P> for $name<$($var),*>
            where
                $($var: IntoResponse<P>,)*
            {
                #[allow(non_snake_case)]
                fn into_response(self) -> http::Response<crate::body::BoxBody> {
                    match self {
                        $($name::$var ($var) => $var.into_response(),)*
                    }
                }
            }
        )
    }

    // any_rejection!(One, A);
    any_rejection!(Two, A, B);
    any_rejection!(Three, A, B, C);
    any_rejection!(Four, A, B, C, D);
    any_rejection!(Five, A, B, C, D, E);
    any_rejection!(Six, A, B, C, D, E, F);
    any_rejection!(Seven, A, B, C, D, E, F, G);
    any_rejection!(Eight, A, B, C, D, E, F, G, H);
}
